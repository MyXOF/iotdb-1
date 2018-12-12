package cn.edu.tsinghua.tsfile.read;

import cn.edu.tsinghua.tsfile.read.expression.IExpression;
import cn.edu.tsinghua.tsfile.read.expression.impl.GlobalTimeExpression;
import cn.edu.tsinghua.tsfile.read.expression.impl.BinaryExpression;
import cn.edu.tsinghua.tsfile.read.expression.impl.SingleSeriesExpression;
import cn.edu.tsinghua.tsfile.utils.Binary;
import cn.edu.tsinghua.tsfile.read.filter.TimeFilter;
import cn.edu.tsinghua.tsfile.read.filter.ValueFilter;
import cn.edu.tsinghua.tsfile.read.common.Path;
import cn.edu.tsinghua.tsfile.read.common.Field;
import cn.edu.tsinghua.tsfile.read.common.RowRecord;
import cn.edu.tsinghua.tsfile.read.query.dataset.QueryDataSet;
import cn.edu.tsinghua.tsfile.read.expression.QueryExpression;
import cn.edu.tsinghua.tsfile.exception.write.WriteProcessException;
import cn.edu.tsinghua.tsfile.utils.FileGenerator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ReadTest {

    private static String fileName = "src/test/resources/perTestOutputData.tsfile";
    private static ReadOnlyTsFile roTsFile = null;

    @Before
    public void prepare() throws IOException, InterruptedException, WriteProcessException {
        FileGenerator.generateFile();
        TsFileSequenceReader reader = new TsFileSequenceReader(fileName, true);
        roTsFile = new ReadOnlyTsFile(reader);
    }

    @After
    public void after() throws IOException {
        if (roTsFile != null)
            roTsFile.close();
        FileGenerator.after();
    }

    @Test
    public void queryOneMeasurementWithoutFilterTest() throws IOException {
        List<Path> pathList = new ArrayList<>();
        pathList.add(new Path("d1.s1"));
        QueryExpression queryExpression = QueryExpression.create(pathList, null);
        QueryDataSet dataSet = roTsFile.query(queryExpression);

        int count = 0;
        while (dataSet.hasNext()) {
            RowRecord r = dataSet.next();
            if (count == 0) {
                assertEquals(r.getTimestamp(), 1480562618010L);
            }
            if (count == 499) {
                assertEquals(r.getTimestamp(), 1480562618999L);
            }
            count++;
        }
        assertEquals(500, count);
    }

    @Test
    public void queryTwoMeasurementsWithoutFilterTest() throws IOException {
        List<Path> pathList = new ArrayList<>();
        pathList.add(new Path("d1.s1"));
        pathList.add(new Path("d2.s2"));
        QueryExpression queryExpression = QueryExpression.create(pathList, null);
        QueryDataSet dataSet = roTsFile.query(queryExpression);

        int count = 0;
        while (dataSet.hasNext()) {
            RowRecord r = dataSet.next();
            if (count == 0) {
                if (count == 0) {
                    assertEquals(1480562618005L, r.getTimestamp());
                }
            }
            count++;
        }
        assertEquals(count, 750);
    }

    @Test
    public void queryTwoMeasurementsWithSingleFilterTest() throws IOException {
        List<Path> pathList = new ArrayList<>();
        pathList.add(new Path("d2.s1"));
        pathList.add(new Path("d2.s4"));
        IExpression valFilter = new SingleSeriesExpression(new Path("d2.s2"), ValueFilter.gt(9722L));
        IExpression tFilter = BinaryExpression.and(new GlobalTimeExpression(TimeFilter.gtEq(1480562618970L)),
                new GlobalTimeExpression(TimeFilter.lt(1480562618977L)));
        IExpression finalFilter = BinaryExpression.and(valFilter, tFilter);
        QueryExpression queryExpression = QueryExpression.create(pathList, finalFilter);
        QueryDataSet dataSet = roTsFile.query(queryExpression);

        while (dataSet.hasNext()) {
            dataSet.next();
        }
    }

    @Test
    public void queryOneMeasurementsWithSameFilterTest() throws IOException {
        List<Path> pathList = new ArrayList<>();
        pathList.add(new Path("d2.s2"));
        IExpression valFilter = new SingleSeriesExpression(new Path("d2.s2"), ValueFilter.gt(9722L));
        QueryExpression queryExpression = QueryExpression.create(pathList, valFilter);
        QueryDataSet dataSet = roTsFile.query(queryExpression);

        int cnt = 0;
        while (dataSet.hasNext()) {
            RowRecord record = dataSet.next();
            Field value = record.getFields().get(0);
            if (cnt == 0) {
                assertEquals(record.getTimestamp(), 1480562618973L);
                assertEquals(value.getLongV(), 9732);
            } else if (cnt == 1) {
                assertEquals(record.getTimestamp(), 1480562618974L);
                assertEquals(value.getLongV(), 9742);
            } else if (cnt == 7) {
                assertEquals(record.getTimestamp(), 1480562618985L);
                assertEquals(value.getLongV(), 9852);
            }

            cnt++;
            //System.out.println(record.toString());
        }
    }

    @Test
    public void queryWithTwoSeriesTimeValueFilterCrossTest() throws IOException {
        List<Path> pathList = new ArrayList<>();
        pathList.add(new Path("d1.s1"));
        pathList.add(new Path("d2.s2"));
        IExpression valFilter = new SingleSeriesExpression(new Path("d2.s2"), ValueFilter.notEq(9722L));
        IExpression tFilter = BinaryExpression.and(new GlobalTimeExpression(TimeFilter.gtEq(1480562618970L)),
                new GlobalTimeExpression(TimeFilter.lt(1480562618977L)));
        IExpression finalFilter = BinaryExpression.and(valFilter, tFilter);
        QueryExpression queryExpression = QueryExpression.create(pathList, finalFilter);
        QueryDataSet dataSet = roTsFile.query(queryExpression);

        // time filter & value filter
        // verify d1.s1, d2.s1
        int cnt = 1;
        while (dataSet.hasNext()) {
            RowRecord r = dataSet.next();
            if (cnt == 1) {
                assertEquals(1480562618970L, r.getTimestamp());
            } else if (cnt == 2) {
                assertEquals(1480562618971L, r.getTimestamp());
            } else if (cnt == 3) {
                assertEquals(1480562618973L, r.getTimestamp());
            }
            // System.out.println(r);
            cnt++;
        }
        assertEquals(7, cnt);
    }

    @Test
    public void queryWithCrossSeriesTimeValueFilterTest() throws IOException {
        List<Path> pathList = new ArrayList<>();
        pathList.add(new Path("d1.s1"));
        pathList.add(new Path("d2.s2"));
        IExpression valFilter = new SingleSeriesExpression(new Path("d2.s2"), ValueFilter.notEq(9722L));
        IExpression tFilter = BinaryExpression.and(new GlobalTimeExpression(TimeFilter.gtEq(1480562618970L)),
                new GlobalTimeExpression(TimeFilter.lt(1480562618975L)));
        IExpression finalFilter = BinaryExpression.and(valFilter, tFilter);
        QueryExpression queryExpression = QueryExpression.create(pathList, finalFilter);
        QueryDataSet dataSet = roTsFile.query(queryExpression);

        // time filter & value filter
        // verify d1.s1, d2.s1
        /**
         1480562618950	9501	9502
         1480562618954	9541	9542
         1480562618955	9551	9552
         1480562618956	9561	9562
         */
        int cnt = 1;
        while (dataSet.hasNext()) {
            RowRecord r = dataSet.next();
            if (cnt == 1) {
                assertEquals(r.getTimestamp(), 1480562618970L);
            } else if (cnt == 2) {
                assertEquals(r.getTimestamp(), 1480562618971L);
            } else if (cnt == 3) {
                assertEquals(r.getTimestamp(), 1480562618973L);
            } else if (cnt == 4) {
                assertEquals(r.getTimestamp(), 1480562618974L);
            }
            //System.out.println(r);
            cnt++;
        }
        assertEquals(cnt, 5);

        pathList.clear();
        pathList.add(new Path("d1.s1"));
        pathList.add(new Path("d2.s2"));
        valFilter = new SingleSeriesExpression(new Path("d2.s2"), ValueFilter.ltEq(9082L));
        tFilter = BinaryExpression.and(new GlobalTimeExpression(TimeFilter.gtEq(1480562618906L)),
                new GlobalTimeExpression(TimeFilter.ltEq(1480562618915L)));
        tFilter = BinaryExpression.or(tFilter,
                BinaryExpression.and(new GlobalTimeExpression(TimeFilter.gtEq(1480562618928L)),
                        new GlobalTimeExpression(TimeFilter.ltEq(1480562618933L))));
        finalFilter = BinaryExpression.and(valFilter, tFilter);
        queryExpression = QueryExpression.create(pathList, finalFilter);
        dataSet = roTsFile.query(queryExpression);

        // time filter & value filter
        // verify d1.s1, d2.s1
        cnt = 1;
        while (dataSet.hasNext()) {
            dataSet.next();
            cnt++;
        }
        assertEquals(cnt, 4);
    }

    @Test
    public void queryBooleanTest() throws IOException {
        List<Path> pathList = new ArrayList<>();
        pathList.add(new Path("d1.s5"));
        IExpression valFilter = new SingleSeriesExpression(new Path("d1.s5"), ValueFilter.eq(false));
        IExpression tFilter = BinaryExpression.and(new GlobalTimeExpression(TimeFilter.gtEq(1480562618970L)),
                new GlobalTimeExpression(TimeFilter.lt(1480562618981L)));
        IExpression finalFilter = BinaryExpression.and(valFilter, tFilter);
        QueryExpression queryExpression = QueryExpression.create(pathList, finalFilter);
        QueryDataSet dataSet = roTsFile.query(queryExpression);

        int cnt = 1;
        while (dataSet.hasNext()) {
            RowRecord r = dataSet.next();
            if (cnt == 1) {
                assertEquals(r.getTimestamp(), 1480562618972L);
                Field f1 = r.getFields().get(0);
                assertEquals(f1.getBoolV(), false);
            }
            if (cnt == 2) {
                assertEquals(r.getTimestamp(), 1480562618981L);
                Field f2 = r.getFields().get(0);
                assertEquals(f2.getBoolV(), false);
            }
            cnt++;
        }
    }

    @Test
    public void queryStringTest() throws IOException {
        List<Path> pathList = new ArrayList<>();
        pathList.add(new Path("d1.s4"));
        IExpression valFilter = new SingleSeriesExpression(new Path("d1.s4"), ValueFilter.gt(new Binary("dog97")));
        IExpression tFilter = BinaryExpression.and(new GlobalTimeExpression(TimeFilter.gtEq(1480562618970L)),
                new GlobalTimeExpression(TimeFilter.ltEq(1480562618981L)));
        IExpression finalFilter = BinaryExpression.and(valFilter, tFilter);
        QueryExpression queryExpression = QueryExpression.create(pathList, finalFilter);
        QueryDataSet dataSet = roTsFile.query(queryExpression);

        int cnt = 0;
        while (dataSet.hasNext()) {
            RowRecord r = dataSet.next();
            if (cnt == 0) {
                assertEquals(r.getTimestamp(), 1480562618976L);
                Field f1 = r.getFields().get(0);
                assertEquals(f1.toString(), "dog976");
            }
            // System.out.println(r);
            cnt++;
        }
        Assert.assertEquals(cnt, 1);

        pathList = new ArrayList<>();
        pathList.add(new Path("d1.s4"));
        valFilter = new SingleSeriesExpression(new Path("d1.s4"), ValueFilter.lt(new Binary("dog97")));
        tFilter = BinaryExpression.and(new GlobalTimeExpression(TimeFilter.gtEq(1480562618970L)),
                new GlobalTimeExpression(TimeFilter.ltEq(1480562618981L)));
        finalFilter = BinaryExpression.and(valFilter, tFilter);
        queryExpression = QueryExpression.create(pathList, finalFilter);
        dataSet = roTsFile.query(queryExpression);
        cnt = 0;
        while (dataSet.hasNext()) {
            RowRecord r = dataSet.next();
            if (cnt == 1) {
                assertEquals(r.getTimestamp(), 1480562618976L);
                Field f1 = r.getFields().get(0);
                assertEquals(f1.getBinaryV().getStringValue(), "dog976");
            }
            // System.out.println(r);
            cnt++;
        }
        Assert.assertEquals(cnt, 0);

    }

    @Test
    public void queryFloatTest() throws IOException {
        List<Path> pathList = new ArrayList<>();
        pathList.add(new Path("d1.s6"));
        IExpression valFilter = new SingleSeriesExpression(new Path("d1.s6"), ValueFilter.gt(103.0f));
        IExpression tFilter = BinaryExpression.and(new GlobalTimeExpression(TimeFilter.gtEq(1480562618970L)),
                new GlobalTimeExpression(TimeFilter.ltEq(1480562618981L)));
        IExpression finalFilter = BinaryExpression.and(valFilter, tFilter);
        QueryExpression queryExpression = QueryExpression.create(pathList, finalFilter);
        QueryDataSet dataSet = roTsFile.query(queryExpression);

        int cnt = 0;
        while (dataSet.hasNext()) {
            RowRecord r = dataSet.next();
            if (cnt == 1) {
                assertEquals(r.getTimestamp(), 1480562618980L);
                Field f1 = r.getFields().get(0);
                assertEquals(f1.getFloatV(), 108.0, 0.0);
            }
            if (cnt == 2) {
                assertEquals(r.getTimestamp(), 1480562618990L);
                Field f2 = r.getFields().get(0);
                assertEquals(f2.getFloatV(), 110.0, 0.0);
            }
            cnt++;
        }
    }

    @Test
    public void queryDoubleTest() throws IOException {
        List<Path> pathList = new ArrayList<>();
        pathList.add(new Path("d1.s7"));
        IExpression valFilter = new SingleSeriesExpression(new Path("d1.s7"), ValueFilter.gt(7.0));
        IExpression tFilter = BinaryExpression.and(new GlobalTimeExpression(TimeFilter.gtEq(1480562618021L)),
                new GlobalTimeExpression(TimeFilter.ltEq(1480562618033L)));
        IExpression finalFilter = BinaryExpression.and(valFilter, tFilter);
        QueryExpression queryExpression = QueryExpression.create(pathList, finalFilter);
        QueryDataSet dataSet = roTsFile.query(queryExpression);

        int cnt = 1;
        while (dataSet.hasNext()) {
            RowRecord r = dataSet.next();
            if (cnt == 1) {
                assertEquals(r.getTimestamp(), 1480562618022L);
                Field f1 = r.getFields().get(0);
                assertEquals(f1.getDoubleV(), 2.0, 0.0);
            }
            if (cnt == 2) {
                assertEquals(r.getTimestamp(), 1480562618033L);
                Field f1 = r.getFields().get(0);
                assertEquals(f1.getDoubleV(), 3.0, 0.0);
            }
            cnt++;
        }
    }
}