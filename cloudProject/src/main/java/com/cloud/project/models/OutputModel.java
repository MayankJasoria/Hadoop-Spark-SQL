package com.cloud.project.models;

public class OutputModel {

    // hadoop parameters
    private String hadoopExecutionTime;
    private String GroupByMapperPlan;
    private String GroupByReducerPlan;
    private String FirstMapperPlan;
    private String SecondMapperPlan;
    private String InnerJoinReducerPlan;
    private String hadoopOutputUrl;

    // spark parameters
    private String sparkExecutionTime;
    private String sparkPlan;
    private String sparkOutputUrl;

    public OutputModel() {
        hadoopExecutionTime = null;
        GroupByMapperPlan = null;
        GroupByReducerPlan = null;
        FirstMapperPlan = null;
        SecondMapperPlan = null;
        InnerJoinReducerPlan = null;
        hadoopOutputUrl = null;
        sparkExecutionTime = null;
        sparkPlan = null;
        sparkOutputUrl = null;
    }

    public String getHadoopExecutionTime() {
        return hadoopExecutionTime;
    }

    public void setHadoopExecutionTime(String hadoopExecutionTime) {
        this.hadoopExecutionTime = hadoopExecutionTime;
    }

    public String getGroupByMapperPlan() {
        return GroupByMapperPlan;
    }

    public void setGroupByMapperPlan(String groupByMapperPlan) {
        GroupByMapperPlan = groupByMapperPlan;
    }

    public String getGroupByReducerPlan() {
        return GroupByReducerPlan;
    }

    public void setGroupByReducerPlan(String groupByReducerPlan) {
        GroupByReducerPlan = groupByReducerPlan;
    }

    public String getFirstMapperPlan() {
        return FirstMapperPlan;
    }

    public void setFirstMapperPlan(String firstMapperPlan) {
        this.FirstMapperPlan = firstMapperPlan;
    }

    public String getSecondMapperPlan() {
        return SecondMapperPlan;
    }

    public void setSecondMapperPlan(String secondMapperPlan) {
        this.SecondMapperPlan = secondMapperPlan;
    }

    public String getInnerJoinReducerPlan() {
        return InnerJoinReducerPlan;
    }

    public void setInnerJoinReducerPlan(String innerJoinReducerPlan) {
        this.InnerJoinReducerPlan = innerJoinReducerPlan;
    }

    public String getHadoopOutputUrl() {
        return hadoopOutputUrl;
    }

    public void setHadoopOutputUrl(String hadoopOutputUrl) {
        this.hadoopOutputUrl = hadoopOutputUrl;
    }

    public String getSparkExecutionTime() {
        return sparkExecutionTime;
    }

    public void setSparkExecutionTime(String sparkExecutionTime) {
        this.sparkExecutionTime = sparkExecutionTime;
    }

    public String getSparkPlan() {
        return sparkPlan;
    }

    public void setSparkPlan(String sparkPlan) {
        this.sparkPlan = sparkPlan;
    }

    public String getSparkOutputUrl() {
        return sparkOutputUrl;
    }

    public void setSparkOutputUrl(String sparkOutputUrl) {
        this.sparkOutputUrl = sparkOutputUrl;
    }
}
