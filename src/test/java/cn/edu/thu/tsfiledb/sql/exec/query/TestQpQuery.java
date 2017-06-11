package cn.edu.thu.tsfiledb.sql.exec.query;

import cn.edu.thu.tsfile.common.constant.SystemConstant;
import cn.edu.thu.tsfile.timeseries.read.qp.Path;
import cn.edu.thu.tsfile.timeseries.read.query.QueryDataSet;
import cn.edu.thu.tsfile.timeseries.utils.StringContainer;
import cn.edu.thu.tsfiledb.qp.exception.QueryProcessorException;
import cn.edu.thu.tsfiledb.qp.logical.operator.root.RootOperator;
import cn.edu.thu.tsfiledb.sql.exec.TSqlParserV2;
import cn.edu.thu.tsfiledb.sql.exec.utils.MemIntQpExecutor;
import org.antlr.runtime.RecognitionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


/**
 * test query operation
 * 
 * @author kangrong
 *
 */
@RunWith(Parameterized.class)
public class TestQpQuery {
    private static final Logger LOG = LoggerFactory.getLogger(TestQpQuery.class);
    private MemIntQpExecutor exec = new MemIntQpExecutor();

    @Before
    public void before() {
        Path path1 = new Path(new StringContainer(
                new String[]{"root", "laptop", "device_1", "sensor_1"},
                SystemConstant.PATH_SEPARATOR));
        Path path2 = new Path(new StringContainer(
                new String[]{"root", "laptop", "device_1", "sensor_2"},
                SystemConstant.PATH_SEPARATOR));
        for (int i = 1; i <= 10; i++) {
            exec.insert(path1, i * 20, Integer.toString(i * 20 + 1));
            exec.insert(path2, i * 50, Integer.toString(i * 50 + 2));
        }
    }

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays
                .asList(new Object[][] {
                        // test time,
                        {
                                "select sensor_1,sensor_2 from root.laptop.device_1 where time <= 51",
                                new String[] {
                                        "20, <root.laptop.device_1.sensor_1,21> <root.laptop.device_1.sensor_2,null> ",
                                        "40, <root.laptop.device_1.sensor_1,41> <root.laptop.device_1.sensor_2,null> ",
                                        "50, <root.laptop.device_1.sensor_1,null> <root.laptop.device_1.sensor_2,52> "},
                                null},
                        // test complex time,
                        {
                                "select sensor_1,sensor_2 " + "from root.laptop.device_1 "
                                        + "where time <= 51 or (time != 100 and time > 460)",
                                new String[] {
                                        "20, <root.laptop.device_1.sensor_1,21> <root.laptop.device_1.sensor_2,null> ",
                                        "40, <root.laptop.device_1.sensor_1,41> <root.laptop.device_1.sensor_2,null> ",
                                        "50, <root.laptop.device_1.sensor_1,null> <root.laptop.device_1.sensor_2,52> ",
                                        "500, <root.laptop.device_1.sensor_1,null> <root.laptop.device_1.sensor_2,502> "},
                                null},
                        // test not
                        {
                                "select sensor_1,sensor_2 " + "from root.laptop.device_1 "
                                        + "where time <= 51 or !(time != 100 and time < 460)",
                                new String[] {
                                        "20, <root.laptop.device_1.sensor_1,21> <root.laptop.device_1.sensor_2,null> ",
                                        "40, <root.laptop.device_1.sensor_1,41> <root.laptop.device_1.sensor_2,null> ",
                                        "50, <root.laptop.device_1.sensor_1,null> <root.laptop.device_1.sensor_2,52> ",
                                        "100, <root.laptop.device_1.sensor_1,101> <root.laptop.device_1.sensor_2,102> ",
                                        "500, <root.laptop.device_1.sensor_1,null> <root.laptop.device_1.sensor_2,502> "},
                                null},
                        // test DNF, just test DNF transform original expression to a conjunction
//                        {
//                                "select sensor_1,sensor_2 "
//                                        + "from root.laptop.device_1 "
//                                        + "where time <= 20 and (sensor_1 >= 60 or sensor_1 <= 110)",
//                                new String[] {"20, <root.laptop.device_1.sensor_1,21> <root.laptop.device_1.sensor_2,null> "},
//                                null},
                        // test DNF2
//                        {
//
//                                "select sensor_1,sensor_2 "
//                                        + "from root.laptop.device_1 "
//                                        + "where time < 150 and (sensor_1 > 30 or time >= 60)",
//                                new String[] {
//                                        "40, <root.laptop.device_1.sensor_1,41> <root.laptop.device_1.sensor_2,null> ",
//                                        "60, <root.laptop.device_1.sensor_1,61> <root.laptop.device_1.sensor_2,null> ",
//                                        "80, <root.laptop.device_1.sensor_1,81> <root.laptop.device_1.sensor_2,null> ",
//                                        "100, <root.laptop.device_1.sensor_1,101> <root.laptop.device_1.sensor_2,102> ",
//                                        "120, <root.laptop.device_1.sensor_1,121> <root.laptop.device_1.sensor_2,null> ",
//                                        "140, <root.laptop.device_1.sensor_1,141> <root.laptop.device_1.sensor_2,null> "},
//                                null},
                        // test Merge
                        {
                                "select sensor_1,sensor_2 " + "from root.laptop.device_1 "
                                        + "where time < 150 and sensor_1 >= 20 and time = 60",
                                new String[] {"60, <root.laptop.device_1.sensor_1,61> <root.laptop.device_1.sensor_2,null> "},
                                null}
                                });
    }

    private final String inputSQL;
    private final String[] result;
    private final Exception expectException;

    public TestQpQuery(String inputSQL, String[] result, Exception expectException) {
        this.inputSQL = inputSQL;
        this.result = result;
        this.expectException = expectException;
    }

    @Test
    public void testQueryBasic() throws QueryProcessorException, RecognitionException {
        LOG.info("input SQL String:{}", inputSQL);
        TSqlParserV2 parser = new TSqlParserV2();
        RootOperator root = parser.parseSQLToOperator(inputSQL);
        if (!root.isQuery())
            fail();
        Iterator<QueryDataSet> iter = parser.query(root, exec);
        LOG.info("query result:");
        int i = 0;
        while (iter.hasNext()) {
            QueryDataSet set = iter.next();
            while (set.hasNextRecord()) {
                if (i == result.length)
                    fail();
                String actual = set.getNextRecord().toString();
                System.out.println(actual);
                assertEquals(result[i++], actual);
            }
        }
        assertEquals(result.length, i);
        LOG.info("Query processing complete\n");
    }
}
