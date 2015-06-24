/*
 * This file is part of Gradoop.
 *
 * Gradoop is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Gradoop is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Gradoop.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.gradoop.biiig.examples;

import com.google.common.collect.Sets;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.time.StopWatch;
import org.apache.giraph.conf.GiraphConfiguration;
import org.apache.giraph.conf.GiraphConstants;
import org.apache.giraph.job.GiraphJob;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat2;
import org.apache.hadoop.hbase.mapreduce.LoadIncrementalHFiles;
import org.apache.hadoop.hbase.mapreduce.TableInputFormat;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableOutputFormat;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;
import org.gradoop.GConstants;
import org.gradoop.algorithms.PairAggregator;
import org.gradoop.algorithms.SelectAndAggregate;
import org.gradoop.algorithms.Sort;
import org.gradoop.biiig.BIIIGConstants;
import org.gradoop.biiig.algorithms.BTGComputation;
import org.gradoop.biiig.io.formats.BTGHBaseVertexInputFormat;
import org.gradoop.biiig.io.formats.BTGHBaseVertexOutputFormat;
import org.gradoop.biiig.io.reader.FoodBrokerReader;
import org.gradoop.io.formats.GenericPairWritable;
import org.gradoop.io.reader.BulkLoadEPG;
import org.gradoop.io.reader.VertexLineReader;
import org.gradoop.model.Graph;
import org.gradoop.model.Vertex;
import org.gradoop.model.operators.VertexAggregate;
import org.gradoop.model.operators.VertexPredicate;
import org.gradoop.storage.GraphStore;
import org.gradoop.storage.hbase.EPGGraphHandler;
import org.gradoop.storage.hbase.EPGVertexHandler;
import org.gradoop.storage.hbase.GraphHandler;
import org.gradoop.storage.hbase.HBaseGraphStoreFactory;
import org.gradoop.storage.hbase.VertexHandler;
import org.gradoop.utils.ConfigurationUtils;

import java.io.IOException;
import java.util.Set;

/**
 * Runs the BIIIG Foodbroker/BTG Example
 */
public class BTGAnalysisDriver extends Configured implements Tool {
  /**
   * Logger
   */
  private static Logger LOG = Logger.getLogger(BTGAnalysisDriver.class);

  /**
   * Job names start with that prefix.
   */
  private static final String JOB_PREFIX = "BTG Analysis: ";

  static {
    Configuration.addDefaultResource("giraph-site.xml");
    Configuration.addDefaultResource("hbase-site.xml");
  }

  /**
   * Used to read and write graph elements from HBase.
   */
  private GraphStore graphStore;

  /**
   * Starting point for BTG analysis pipeline.
   *
   * @param args driver arguments
   * @return Exit code (0 - ok)
   * @throws Exception
   */
  @Override
  public int run(String[] args) throws Exception {

    Configuration conf = getConf();
    CommandLine cmd = ConfigurationUtils.parseArgs(args);

    if (cmd == null) {
      return 0;
    }

    boolean verbose = cmd.hasOption(ConfigurationUtils.OPTION_VERBOSE);

    /*
    Step 0: Delete (if exists) and create HBase tables
     */
    if (cmd.hasOption(ConfigurationUtils.OPTION_DROP_TABLES)) {
      HBaseGraphStoreFactory.deleteGraphStore(conf);
    }

    graphStore = HBaseGraphStoreFactory
      .createOrOpenGraphStore(conf, new EPGVertexHandler(),
        new EPGGraphHandler());

    /*
    Step 1: Bulk Load of the graph into HBase using MapReduce
     */
    String inputPath =
      cmd.getOptionValue(ConfigurationUtils.OPTION_GRAPH_INPUT_PATH);
    String outputPath =
      cmd.getOptionValue(ConfigurationUtils.OPTION_GRAPH_OUTPUT_PATH);
    if (!runBulkLoad(conf, inputPath, outputPath, verbose)) {
      return -1;
    }

    /*
    Step 2: BTG Computation using Giraph
     */
    int workers =
      Integer.parseInt(cmd.getOptionValue(ConfigurationUtils.OPTION_WORKERS));
    if (!runBTGComputation(conf, workers, verbose)) {
      return -1;
    }

    /*
    Step 3: Select And Aggregate using MapReduce
     */
    int scanCache = Integer.parseInt(
      cmd.getOptionValue(ConfigurationUtils.OPTION_HBASE_SCAN_CACHE, "500"));
    int reducers = Integer
      .parseInt(cmd.getOptionValue(ConfigurationUtils.OPTION_REDUCERS, "1"));
    if (!runSelectAndAggregate(conf, scanCache, reducers, verbose)) {
      return -1;
    }

    /*
    Step 4: Sort graphs by profit
     */
    String sortTableName =
      cmd.getOptionValue(ConfigurationUtils.OPTION_SORT_TABLE_NAME);
    if (!runSort(conf, sortTableName, scanCache, verbose)) {
      return -1;
    }

    /*
    Step 5: Compute overlapping nodes of top 100 graphs
     */
    StopWatch sw = new StopWatch();
    sw.start();
    if (!runOverlap(conf, sortTableName, 100)) {
      return -1;
    }
    sw.stop();
    LOG.info(String.format("Overlap took: %d seconds", sw.getTime() / 1000));

    return 0;
  }

  /**
   * Runs the HFile conversion from the given file to the output dir. Also
   * loads the HFiles to region servers.
   *
   * @param conf      Cluster config
   * @param graphFile input file in HDFS
   * @param outDir    HFile output dir in HDFS
   * @param verbose   print output during job
   * @return true, if the job completed successfully, false otherwise
   * @throws IOException
   */
  private boolean runBulkLoad(Configuration conf, String graphFile,
    String outDir, boolean verbose) throws Exception {
    Path inputFile = new Path(graphFile);
    Path outputDir = new Path(outDir);

    // set line reader to read lines in input splits
    conf.setClass(BulkLoadEPG.VERTEX_LINE_READER, FoodBrokerReader.class,
      VertexLineReader.class);
    // set vertex handler that creates the Puts
    conf.setClass(BulkLoadEPG.VERTEX_HANDLER, EPGVertexHandler.class,
      VertexHandler.class);

    Job job = Job.getInstance(conf, JOB_PREFIX + BulkLoadEPG.class.getName());
    job.setJarByClass(BulkLoadEPG.class);
    // mapper that runs the HFile conversion
    job.setMapperClass(BulkLoadEPG.class);
    // input format for Mapper (File)
    job.setInputFormatClass(TextInputFormat.class);
    // output Key class of Mapper
    job.setMapOutputKeyClass(ImmutableBytesWritable.class);
    // output Value class of Mapper
    job.setMapOutputValueClass(Put.class);

    // set input file
    FileInputFormat.addInputPath(job, inputFile);
    // set output directory
    FileOutputFormat.setOutputPath(job, outputDir);

    HTable hTable = new HTable(conf, GConstants.DEFAULT_TABLE_VERTICES);

    // auto configure partitioner and reducer corresponding to the number of
    // regions
    HFileOutputFormat2.configureIncrementalLoad(job, hTable);

    // run job
    if (!job.waitForCompletion(verbose)) {
      LOG.error("Error during bulk import ... stopping pipeline");
      return false;
    }

    // load created HFiles to the region servers
    LoadIncrementalHFiles loader = new LoadIncrementalHFiles(conf);
    loader.doBulkLoad(outputDir, hTable);

    return true;
  }

  /**
   * Runs the BTG computation on the input graph using Giraph.
   *
   * @param conf        Cluster configuration
   * @param workerCount Number of workers Giraph shall use
   * @param verbose     print output during job
   * @return true, if the job completed successfully, false otherwise
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws InterruptedException
   * @throws ParseException
   */
  private boolean runBTGComputation(Configuration conf, int workerCount,
    boolean verbose) throws IOException, ClassNotFoundException,
    InterruptedException, ParseException {
    // set HBase table to read graph from
    conf.set(TableInputFormat.INPUT_TABLE, GConstants.DEFAULT_TABLE_VERTICES);
    // just scan necessary CFs (no properties needed)
    String columnFamiliesToScan = String
      .format("%s %s %s %s", GConstants.CF_META, GConstants.CF_OUT_EDGES,
        GConstants.CF_IN_EDGES, GConstants.CF_GRAPHS);
    conf.set(TableInputFormat.SCAN_COLUMNS, columnFamiliesToScan);
    // set HBase table to write computation results to
    conf.set(TableOutputFormat.OUTPUT_TABLE, GConstants.DEFAULT_TABLE_VERTICES);


    // setup Giraph job
    GiraphJob job =
      new GiraphJob(conf, JOB_PREFIX + BTGComputation.class.getName());
    GiraphConfiguration giraphConf = job.getConfiguration();

    giraphConf.setComputationClass(BTGComputation.class);
    giraphConf.setVertexInputFormatClass(BTGHBaseVertexInputFormat.class);
    giraphConf.setVertexOutputFormatClass(BTGHBaseVertexOutputFormat.class);
    giraphConf.setWorkerConfiguration(workerCount, workerCount, 100f);

    // assuming local environment
    if (workerCount == 1) {
      GiraphConstants.SPLIT_MASTER_WORKER.set(giraphConf, false);
      GiraphConstants.LOCAL_TEST_MODE.set(giraphConf, true);
    }

    return job.run(verbose);
  }

  /**
   * Runs Selection and Aggregation using a single MapReduce Job.
   *
   * @param conf      Cluster configuration
   * @param scanCache hbase client scan cache
   * @param reducers  number of reducers to use for the job
   * @param verbose   print output during job
   * @return true, if the job completed successfully, false otherwise
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws InterruptedException
   */
  private boolean runSelectAndAggregate(Configuration conf, int scanCache,
    int reducers, boolean verbose) throws IOException, ClassNotFoundException,
    InterruptedException {
    /*
     mapper settings
      */
    // vertex handler
    conf.setClass(GConstants.VERTEX_HANDLER_CLASS, EPGVertexHandler.class,
      VertexHandler.class);

    // vertex predicate
    conf.setClass(SelectAndAggregate.VERTEX_PREDICATE_CLASS,
      SalesOrderPredicate.class, VertexPredicate.class);

    // vertex aggregate
    conf.setClass(SelectAndAggregate.VERTEX_AGGREGATE_CLASS,
      ProfitVertexAggregate.class, VertexAggregate.class);

    /*
    reducer settings
     */
    // graph handler
    conf.setClass(GConstants.GRAPH_HANDLER_CLASS, EPGGraphHandler.class,
      GraphHandler.class);
    // pair aggregate class
    conf.setClass(SelectAndAggregate.PAIR_AGGREGATE_CLASS, SumAggregator.class,
      PairAggregator.class);

    Job job =
      Job.getInstance(conf, JOB_PREFIX + SelectAndAggregate.class.getName());
    Scan scan = new Scan();
    scan.setCaching(scanCache);
    scan.setCacheBlocks(false);

    // map
    TableMapReduceUtil
      .initTableMapperJob(GConstants.DEFAULT_TABLE_VERTICES, scan,
        SelectAndAggregate.SelectMapper.class, LongWritable.class,
        GenericPairWritable.class, job);

    // reduce
    TableMapReduceUtil.initTableReducerJob(GConstants.DEFAULT_TABLE_GRAPHS,
      SelectAndAggregate.AggregateReducer.class, job);
    job.setNumReduceTasks(reducers);

    // run
    return job.waitForCompletion(verbose);
  }

  /**
   * Sorts an HBase table by a given column value.
   *
   * @param conf          Cluster configuration
   * @param sortTableName table to use for sort output
   * @param scanCache     hbase client scan cache
   * @param verbose       print output during job
   * @return true, if the job completed successfully, false otherwise
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws InterruptedException
   */
  private boolean runSort(Configuration conf, String sortTableName,
    int scanCache, boolean verbose) throws IOException, ClassNotFoundException,
    InterruptedException {

    String sortKey = SelectAndAggregate.DEFAULT_AGGREGATE_RESULT_PROPERTY_KEY;

    /*
     mapper settings
      */
    // graph handler
    conf.setClass(GConstants.GRAPH_HANDLER_CLASS, EPGGraphHandler.class,
      GraphHandler.class);
    conf.set(Sort.SORT_PROPERTY_KEY, sortKey);
    conf.setBoolean(Sort.SORT_ASCENDING, false);

    Job job = Job.getInstance(conf, JOB_PREFIX + Sort.class.getName());
    Scan scan = new Scan();
    scan.addColumn(Bytes.toBytes(GConstants.CF_PROPERTIES),
      Bytes.toBytes(sortKey));
    scan.setCaching(scanCache);
    scan.setCacheBlocks(false);

    // map
    TableMapReduceUtil.initTableMapperJob(GConstants.DEFAULT_TABLE_GRAPHS, scan,
      Sort.SortMapper.class, ImmutableBytesWritable.class, Put.class, job);
    job.setJarByClass(Sort.class);

    job.setOutputFormatClass(TableOutputFormat.class);
    job.getConfiguration().set(TableOutputFormat.OUTPUT_TABLE, sortTableName);

    // no reducer necessary
    job.setNumReduceTasks(0);

    // run
    return job.waitForCompletion(verbose);
  }

  /**
   * Computes the overlapping vertices of the top x graphs.
   *
   * @param conf      Cluster configuration
   * @param sortTable table to use for sort output
   * @param top       number of rows from sort table
   * @return true, iff there was no error
   * @throws IOException
   */
  private boolean runOverlap(Configuration conf, String sortTable,
    int top) throws IOException {
    HTable table = new HTable(conf, sortTable);

    byte[] cf = Bytes.toBytes(Sort.COLUMN_FAMILY_NAME);
    byte[] col = Bytes.toBytes(Sort.COLUMN_NAME);

    Scan scan = new Scan();
    scan.setCaching(top);

    Set<Long> overlap = Sets.newHashSet();

    ResultScanner scanner = table.getScanner(scan);
    Result res = scanner.next();

    int line = 0;
    while (line < top && res != null) {
      Long graphID = Bytes.toLong(res.getValue(cf, col));
      Graph graph = graphStore.readGraph(graphID);
      if (graph != null && graph.getVertexCount() > 0) {
        Set<Long> vertices = Sets.newHashSet(graph.getVertices());
        if (line == 0) {
          overlap.addAll(vertices);
        } else {
          overlap.retainAll(vertices);
        }
        line++;
        res = scanner.next();
      }
    }

    scanner.close();

    LOG.info("=== Overlapping vertices");
    for (Long vertexID : overlap) {
      LOG.info(vertexID);
    }

    return true;
  }


  /**
   * Predicate to select graphs containing a vertex of type SalesOrder.
   */
  public static class SalesOrderPredicate implements VertexPredicate {
    /**
     * Property key a vertex needs to have.
     */
    private static final String KEY = BIIIGConstants.META_PREFIX + "class";
    /**
     * Property value a vertex needs to have to fulfil predicate.
     */
    private static final String VALUE = "SalesOrder";

    @Override
    public boolean evaluate(Vertex vertex) {
      boolean result = false;
      if (vertex.getPropertyCount() > 0) {
        Object o = vertex.getProperty(KEY);
        result = o != null && o.equals(VALUE);
      }
      return result;
    }
  }

  /**
   * Aggregate function to calculate the profit for a single graph.
   */
  public static class ProfitVertexAggregate implements VertexAggregate {
    /**
     * Profit contains expenses.
     */
    private static final String EXPENSE_KEY = "expense";
    /**
     * Profit contains revenues.
     */
    private static final String REVENUE_KEY = "revenue";

    @Override
    public Writable aggregate(Vertex vertex) {
      double calcValue = 0f;
      if (vertex.getPropertyCount() > 0) {
        Object o = vertex.getProperty(REVENUE_KEY);
        if (o != null) {
          if (o instanceof Integer) {
            calcValue += ((Integer) o).doubleValue();
          } else {
            calcValue += (double) o;
          }
        }
        o = vertex.getProperty(EXPENSE_KEY);
        if (o != null) {
          if (o instanceof Integer) {
            calcValue -= ((Integer) o).doubleValue();
          } else {
            calcValue -= (double) o;
          }
        }
      }
      return new DoubleWritable(calcValue);
    }
  }

  /**
   * Calculates the sum from a given set of values.
   */
  public static class SumAggregator implements PairAggregator {

    @Override
    public Pair<Boolean, ? extends Number> aggregate(
      Iterable<GenericPairWritable> values) {
      double sum = 0;
      boolean predicate = false;
      for (GenericPairWritable value : values) {
        sum = sum + ((DoubleWritable) value.getValue().get()).get();
        if (value.getPredicateResult().get()) {
          predicate = true;
        }
      }
      return new Pair<>(predicate, sum);
    }
  }

  /**
   * Runs the job.
   *
   * @param args command line arguments
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    System.exit(ToolRunner.run(conf, new BTGAnalysisDriver(), args));
  }
}
