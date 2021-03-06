package com.cloud.project.jobUtils;

import com.cloud.project.Globals;
import com.cloud.project.contracts.DBManager;
import com.cloud.project.models.OutputModel;
import com.cloud.project.sqlUtils.ParseSQL;
import com.cloud.project.sqlUtils.Tables;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Time;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

public class InnerJoin {

    /**
     * Global Mapper. Maps input tuple to <join_key, tuple - {join_key}>
     *
     * @param table The table on which mapping has to be done. Received from either
     *              firstMapper or secondMapper
     * @param tableKeyIndex Indicates the column index acting as the join key on this table
     * @param value Tuple received from from firstMapper.map() or secondMapper.map()
     * @param context context information
     * @throws IOException  if hadoop IO fails
     * @throws InterruptedException if the hadoop job was interrupted
     */

    private static void globalMapper(Tables table, int tableKeyIndex, Text value,
                                     Mapper<Object, Text, Text, Text>.Context context)
            throws IOException, InterruptedException {

        String record = value.toString();
        String[] parts = record.split(",");

        /* remove if does not match WHERE clause */
        Tables eqTab = context.getConfiguration().getEnum("eqTab", Tables.NONE);
        if (eqTab == table) {
            String eqCol = context.getConfiguration().get("eqCol");
            int ind = DBManager.getColumnIndex(table, eqCol);
            String val = context.getConfiguration().get("eqVal");
            //System.out.println("<---------> Val: " + val + "parts[ind]: " + parts[ind]);
            if (!(val.trim()).equalsIgnoreCase(parts[ind].trim())) return;
        }
        String jk = parts[tableKeyIndex];
        StringBuilder val = new StringBuilder(table.name() + "#");

        int i = 0;
        int flag = 0;
        if (i != tableKeyIndex) {
            val.append(parts[i]);
        } else {
            flag = 1;
        }
        for (i = 1; i < parts.length; i++) {
            if (i == tableKeyIndex) continue;
            if (flag == 1) {
                val.append(parts[i]);
                flag = 0;
            } else {
                val.append(",").append(parts[i]);
            }
        }
        context.write(new Text(jk), new Text(val.toString()));
    }

    /**
     * Executes the Hadoop Map-Reduce job for a Inner Join query.
     *
     * @param parsedSQL instance of {@link ParseSQL} which contains the relevant tokens from the parsed SQL query
     * @return an instance of {@link OutputModel} populated with relevant fields from Hadoop execution
     * @throws IOException            if Hadoop IO fails
     * @throws InterruptedException   if Hadoop job is interrupted
     * @throws ClassNotFoundException if Hadoop environment fails to find the relevant class
     * @throws SQLException           if the SQL query could not be parsed successfully
     */

    public static OutputModel execute(ParseSQL parsedSQL) throws IOException,
            InterruptedException, ClassNotFoundException, SQLException {

        OutputModel innerJoinOutput = new OutputModel();

        /* get key index for both tables */
        String jk = DBManager.getJoinKey(parsedSQL.getTable1(), parsedSQL.getTable2());
        if (jk == null) {
            System.out.println("No join key exists!");
            System.exit(0);
        }

        /* #########################################################################*/
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", Globals.getNamenodeUrl());
        conf.setEnum("table1", parsedSQL.getTable1()); //args[3]);
        conf.setEnum("table2", parsedSQL.getTable2());
        Tables table1 = parsedSQL.getTable1();
        Tables table2 = parsedSQL.getTable2();
        conf.setEnum("eqTab", parsedSQL.getWhereTable());
        conf.set("eqCol", parsedSQL.getWhereColumn());
        conf.set("eqVal", parsedSQL.getWhereValue());
        conf.set("jk", jk);
        Job job = Job.getInstance(conf, "InnerJoin");
        job.setJarByClass(InnerJoin.class);
        job.setReducerClass(ReduceJoinReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        MultipleInputs.addInputPath(job, new Path(Globals.getCsvInputPath() +
                        DBManager.getFileName(parsedSQL.getTable1())),
                TextInputFormat.class, FirstMapper.class);
        MultipleInputs.addInputPath(job, new Path(Globals.getCsvInputPath() +
                        DBManager.getFileName(parsedSQL.getTable2())),
                TextInputFormat.class, SecondMapper.class);
        Path outputPath = new Path(Globals.getHadoopOutputPath());

        FileOutputFormat.setOutputPath(job, outputPath);
        outputPath.getFileSystem(conf).delete(outputPath, true);
        long startTime = Time.now();
        long endTime = (job.waitForCompletion(true) ? Time.now() : startTime);
        long execTime = endTime - startTime;


        StringBuilder reducerVal = new StringBuilder();
        /* Create mapper scheme */
        StringBuilder firstMapperScheme = new StringBuilder("<serial_number, (");

        // mapper input value
        firstMapperScheme.append(DBManager.getColumnFromIndex(table1, 0));
        for (int i = 1; i < DBManager.getTableSize(table1) - 1; i++) {
            firstMapperScheme.append(", ").append(DBManager.getColumnFromIndex(table1, i));
        }

        // end input, start output
        firstMapperScheme.append(")> ---> <").append(jk).append(", (");

        // mapper output key
        int i = 0;
        int flag = 0;
        if (DBManager.getColumnIndex(table1, jk) == i) {
            flag = 1;
        } else {
            firstMapperScheme.append(DBManager.getColumnFromIndex(table1, 0));
            reducerVal.append(DBManager.getColumnFromIndex(table1, 0));
        }
        for (i = 1; i < DBManager.getTableSize(table1) - 1; i++) {
            if (flag == 1) {
                firstMapperScheme.append(DBManager.getColumnFromIndex(table1, i));
                reducerVal.append(DBManager.getColumnFromIndex(table1, i));
                flag = 0;
            } else {
                firstMapperScheme.append(", ").append(DBManager.getColumnFromIndex(table1, i));
                reducerVal.append(", ").append(DBManager.getColumnFromIndex(table1, i));
            }
        }
        firstMapperScheme.append(")>");


        StringBuilder secondMapperScheme = new StringBuilder("<serial_number, (");

        secondMapperScheme.append(DBManager.getColumnFromIndex(table2, 0));
        for (i = 1; i < DBManager.getTableSize(table2) - 1; i++) {
            secondMapperScheme.append(",").append(DBManager.getColumnFromIndex(table2, i));
        }

        // end input, start output
        secondMapperScheme.append(")> ---> <").append(jk).append(", (");

        // mapper output key
        i = 0;
        flag = 0;
        if (DBManager.getColumnIndex(table2, jk) == i) {
            flag = 1;
        } else {
            secondMapperScheme.append(DBManager.getColumnFromIndex(table2, 0));
            reducerVal.append(", ").append(DBManager.getColumnFromIndex(table2, 0));
        }
        for (i = 1; i < DBManager.getTableSize(table2) - 1; i++) {
            if (flag == 1) {
                secondMapperScheme.append(DBManager.getColumnFromIndex(table2, i));
                reducerVal.append(", ").append(DBManager.getColumnFromIndex(table2, i));
                flag = 0;
            } else {
                secondMapperScheme.append(", ").append(DBManager.getColumnFromIndex(table2, i));
                reducerVal.append(", ").append(DBManager.getColumnFromIndex(table2, i));
            }
        }
        secondMapperScheme.append(")>");


        /* Create reducer scheme */
        String str = "<" + jk + ", (" + reducerVal + ")>";

        /* Set Inner Join output */
        innerJoinOutput.setFirstMapperPlan(firstMapperScheme.toString());
        innerJoinOutput.setSecondMapperPlan(secondMapperScheme.toString());
        String reducerScheme = "<" + jk + ", List(" + reducerVal + ")>" +
                " ---> " + str;
        innerJoinOutput.setInnerJoinReducerPlan(reducerScheme);
        innerJoinOutput.setHadoopExecutionTime(execTime + " milliseconds");
//        innerJoinOutput.setHadoopOutputUrl("http://localhost:9870/output/part-r-00000  (Note: WebDFS should be enabled for this to work)");

        FileSystem fileSystem = outputPath.getFileSystem(conf);
//        fileSystem.listFiles(outputPath, false);
        FileStatus[] fileStatuses = fileSystem.listStatus(outputPath);

        StringBuilder downloadUrl = new StringBuilder();

//        for(FileStatus file : fileStatuses) {
//            downloadUrl.append("http://localhost:9870/webhdfs/v1/").append(file.)
//        }

        for (FileStatus fileStatus : fileStatuses) {
            if (fileStatus.isFile()) {
                String filename = fileStatus.getPath().getName();
                System.out.println(filename);
                if (filename.matches("part-r-[0-9]*")) {
                    downloadUrl.append(Globals.getWebhdfsHost())
                            .append("/webhdfs/v1")
                            .append(Globals.getHadoopOutputPath()).append("/")
                            .append(filename)
                            .append("?op=OPEN\n");
                }
            }
        }

        downloadUrl.append("NOTE: These URLs will work only if WebHDFS is enabled");

        innerJoinOutput.setHadoopOutputUrl(downloadUrl.toString());

        return innerJoinOutput;
    }

    /**
     *  Class for mapping the first of the two tables to be joined.
     */

    private static class FirstMapper extends Mapper<Object, Text, Text, Text> {
        private static Tables table;
        private static int tableKeyIndex;

        @Override
        protected void setup(Context context) {
            table = context.getConfiguration().getEnum("table1", Tables.NONE);
            String jk = context.getConfiguration().get("jk");
            tableKeyIndex = DBManager.getColumnIndex(table, jk);
        }

        @Override
        public void map(Object key, Text value, Context context)
                throws IOException, InterruptedException {
            globalMapper(table, tableKeyIndex, value, context);
        }
    }

    /**
     *  Class for mapping the second of the two tables to be joined.
     */

    private static class SecondMapper extends Mapper<Object, Text, Text, Text> {
        private static Tables table;
        private static int tableKeyIndex;

        @Override
        protected void setup(Context context) {
            table = context.getConfiguration().getEnum("table2", Tables.NONE);
            String jk = context.getConfiguration().get("jk");
            tableKeyIndex = DBManager.getColumnIndex(table, jk);
        }

        @Override
        public void map(Object key, Text value, Context context)
                throws IOException, InterruptedException {
            globalMapper(table, tableKeyIndex, value, context);
        }
    }

    private static class ReduceJoinReducer extends Reducer<Text, Text, Text, Text> {
        private static Tables table1;
        private static Tables table2;

        @Override
        protected void setup(Reducer.Context context) {
            table1 = context.getConfiguration().getEnum("table1", Tables.NONE);
            table2 = context.getConfiguration().getEnum("table2", Tables.NONE);
        }

        @Override
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            ArrayList<String> table1List = new ArrayList<>();
            ArrayList<String> table2List = new ArrayList<>();
            //String name = "";
            //double total = 0.0;
            //int count = 0;
            for (Text t : values) {
                String[] parts = t.toString().split("#");
                if (parts[0].equals(table1.name())) {
                    table1List.add(parts[1]);
                } else if (parts[0].equals(table2.name())) {
                    table2List.add(parts[1]);
                }
            }

            for (String u : table1List) {
                for (String z : table2List) {
                    context.write(new Text(key.toString() + ","), new Text(u + ',' + z));
                }

            }
        }
    }
}
