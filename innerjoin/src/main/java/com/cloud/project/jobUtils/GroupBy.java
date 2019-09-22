package com.cloud.project.jobUtils;

import com.cloud.project.contracts.DBManager;
import com.cloud.project.models.GroupByOutput;
import com.cloud.project.sqlUtils.AggregateFunction;
import com.cloud.project.sqlUtils.ParseSQL;
import com.cloud.project.sqlUtils.Tables;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;


public class GroupBy {

    public static GroupByOutput execute(ParseSQL parsedSQL) throws IOException,
            InterruptedException, ClassNotFoundException, SQLException {

        GroupByOutput groupByOutput = new GroupByOutput();

        Configuration conf = new Configuration();

        // like defined in hdfs-site.xml (required for reading file from hdfs)
        conf.set("fs.defaultFS", "hdfs://localhost:9000");

        // defining properties to be used later by mapper and reducer
        conf.setEnum("table", parsedSQL.getTable1());
        conf.setEnum("aggregateFunction", parsedSQL.getAggregateFunction());
        conf.setInt("comparisonNumber", parsedSQL.getComparisonNumber());
        conf.setStrings("columns", parsedSQL.getColumns().toArray(new String[0]));
        conf.setStrings("operationColumns", parsedSQL.getOperationColumns().toArray(new String[0]));

        // creating job and defining jar
        Job job = Job.getInstance(conf, "GroupBy");
        job.setJarByClass(GroupBy.class);

        // setting combiner class
        job.setCombinerClass(GroupByCombiner.class);

        // setting the reducer
        job.setReducerClass(GroupByReducer.class);

        // defining output
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        // passing the required csv file as file path
        MultipleInputs.addInputPath(job,
                new Path("/" + DBManager.getFileName(parsedSQL.getTable1())),
                TextInputFormat.class, GroupByMapper.class);

        // defining path of output file
        Path outputPath = new Path("/output"); // hardcoded for now
        FileOutputFormat.setOutputPath(job, outputPath);

        // deleting existing outputPath file to allow reusability
        outputPath.getFileSystem(conf).delete(outputPath, true);

        job.waitForCompletion(true);
        long execTime = job.getFinishTime() - job.getStartTime();

        // writing time of execution as output
        groupByOutput.setHadoopExecutionTime(execTime + " milliseconds");

        // creating scheme for mapper
        StringBuilder mapperScheme = new StringBuilder("<serial_number, (");

        // mapper input value
        for (int i = 0; i < parsedSQL.getColumns().size() - 1; i++) {
            mapperScheme.append(parsedSQL.getColumns().get(i));
        }

        // end input, start output
        mapperScheme.append(")> ---> <(");

        // mapper output key
        for (int i = 0; i < parsedSQL.getColumns().size() - 2; i++) {
            mapperScheme.append(parsedSQL.getColumns().get(i)).append(", ");
        }
        mapperScheme.append(parsedSQL.getColumns().get(parsedSQL.getColumns().size() - 2));
        mapperScheme.append("), ");

        String aggCol = null;

        // mapper output value
        switch (parsedSQL.getAggregateFunction()) {
            case COUNT:
                mapperScheme.append("1");
                break;
            case SUM:
            case MAX:
            case MIN:
                aggCol = parsedSQL.getColumns()
                        .get(parsedSQL.getColumns().size() - 1)
                        .split("\\(")[1]
                        .split("\\)")[0];
                mapperScheme.append(aggCol);
            default:
                // not likely to be encountered
                throw new IllegalArgumentException("The aggregate function is not valid");
        }

        // close mapper output
        mapperScheme.append(">");

        // write mapper scheme
        groupByOutput.setGroupByMapperPlan(mapperScheme.toString());

        // creating reducer scheme
        StringBuilder reducerScheme = new StringBuilder("<");

        // reducer input key
        for (int i = 0; i < parsedSQL.getColumns().size() - 2; i++) {
            reducerScheme.append(parsedSQL.getColumns().get(i)).append(", ");
        }

        // reducer input key ends, input value starts
        reducerScheme.append(parsedSQL.getColumns().get(parsedSQL.getColumns().size() - 2)).append("), {");

        // reducer input value
        switch (parsedSQL.getAggregateFunction()) {
            case COUNT:
                reducerScheme.append("1, 1, 1, ... 1");
                break;
            case SUM:
            case MIN:
            case MAX:
                reducerScheme.append(aggCol + "(1), " + aggCol + "(2), ... " + aggCol + "(n)");
        }

        // reducer input ends, output starts
        reducerScheme.append("}> ---> ");

        // reducer output key
        for (int i = 0; i < parsedSQL.getColumns().size() - 2; i++) {
            reducerScheme.append(parsedSQL.getColumns().get(i)).append(", ");
        }

        // reducer output key ends
        reducerScheme.append(parsedSQL.getColumns().get(parsedSQL.getColumns().size() - 2))
                .append("), ");

        // reducer output value
        reducerScheme.append(parsedSQL.getColumns().get(parsedSQL.getColumns().size() - 1)).append(">");

        // setting reducer plan
        groupByOutput.setGroupByReducerPlan(reducerScheme.toString());

        // setting hadoop output URL
        groupByOutput.setHadoopOutputUrl("http://webhdfs/v1/output/part-r-00000?op=OPEN  (Note: WebDFS should be enabled for this to work)");

        return groupByOutput;
    }

    public static class GroupByMapper extends Mapper<Object, Text, Text, Text> {

        private static String[] columns;
        private static AggregateFunction aggregateFunction;
        private static Tables table;
        private static int comparisonNumber;

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            columns = conf.getStrings("columns");
            aggregateFunction = conf.getEnum("aggregateFunction", AggregateFunction.NONE);
            table = conf.getEnum("table", Tables.NONE);
            comparisonNumber = conf.getInt("comparisonNumber", Integer.MIN_VALUE);
            super.setup(context);
        }

        @Override
        public void map(Object key, Text value, Context context)
                throws IOException, InterruptedException {
            String[] record = value.toString().split(",");
            StringBuilder builder = new StringBuilder(
                    record[DBManager.getColumnIndex(table, columns[0])]);
            for (int i = 1; i < columns.length - 1; i++) {
                builder.append(",").append(
                        record[DBManager.getColumnIndex(table, columns[i])]);
            }
            // assuming group by is done on all columns
            Text keyOut = new Text(builder.toString());
            String aggregateColumn = columns[columns.length - 1]
                    .split("\\(")[1]
                    .split("\\)")[0];
            int outputValue = Integer.parseInt(record[DBManager.getColumnIndex(table,
                    aggregateColumn)]);
            switch (aggregateFunction) {
                case MAX:
                case MIN:
                    // same behavior for both
                    if (outputValue > comparisonNumber) {
                        context.write(keyOut, new Text(Integer.toString(outputValue)));
                    }
                    break;
                case SUM:
                    context.write(keyOut, new Text(Integer.toString(outputValue)));
                    break;
                case COUNT:
                    context.write(keyOut, new Text("1"));
                    break;
                default:
                    // not likely to be encountered
                    throw new IllegalArgumentException("The aggregate function is not valid");
            }
        }
    }

    public static class GroupByCombiner extends Reducer<Text, Text, Text, Text> {


        private static AggregateFunction aggregateFunction;
        private static int comparisonNumber;

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            aggregateFunction = conf.getEnum("aggregateFunction", AggregateFunction.NONE);
            comparisonNumber = conf.getInt("comparisonNumber", Integer.MIN_VALUE);
            super.setup(context);
        }

        @Override
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            Iterator<Text> it = values.iterator();
            switch (aggregateFunction) {
                case MIN:
                    int min = Integer.MAX_VALUE;
                    while (it.hasNext()) {
                        min = Math.min(Integer.parseInt(it.next().toString()), min);
                    }
                    if (min > comparisonNumber) {
                        context.write(key, new Text(Integer.toString(min)));
                    }
                    break;
                case MAX:
                    int max = Integer.MIN_VALUE;
                    while (it.hasNext()) {
                        max = Math.max(Integer.parseInt(it.next().toString()), max);
                    }
                    if (max > comparisonNumber) {
                        context.write(key, new Text(Integer.toString(max)));
                    }
                    break;
                case SUM:
                case COUNT:
                    // both have same behavior, except count will take sum of 0s and 1s
                    long sum = 0;
                    while (it.hasNext()) {
                        sum += Integer.parseInt(it.next().toString());
                    }
                    context.write(key, new Text(Long.toString(sum)));
                    break;
                default:
                    // not likely to be encountered
                    throw new IllegalArgumentException("The aggregate function is not valid");
            }
        }
    }

    public static class GroupByReducer extends Reducer<Text, Text, Text, Text> {


        private static AggregateFunction aggregateFunction;
        private static int comparisonNumber;

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            aggregateFunction = conf.getEnum("aggregateFunction", AggregateFunction.NONE);
            comparisonNumber = conf.getInt("comparisonNumber", Integer.MIN_VALUE);
            super.setup(context);
        }

        @Override
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            Iterator<Text> it = values.iterator();
            switch (aggregateFunction) {
                case MIN:
                    int min = Integer.MAX_VALUE;
                    while (it.hasNext()) {
                        min = Math.min(Integer.parseInt(it.next().toString()), min);
                    }
                    if (min > comparisonNumber) {
                        context.write(key, new Text("," + min));
                    }
                    break;
                case MAX:
                    int max = Integer.MIN_VALUE;
                    while (it.hasNext()) {
                        max = Math.max(Integer.parseInt(it.next().toString()), max);
                    }
                    if (max > comparisonNumber) {
                        context.write(key, new Text("," + max));
                    }
                    break;
                case SUM:
                case COUNT:
                    // both have same behavior, except count will take sum of 0s and 1s
                    long sum = 0;
                    while (it.hasNext()) {
                        sum += Integer.parseInt(it.next().toString());
                    }
                    if (sum > comparisonNumber) {
                        context.write(key, new Text("," + sum));
                    }
                    break;
                default:
                    // not likely to be encountered
                    throw new IllegalArgumentException("The aggregate function is not valid");
            }
        }
    }
}