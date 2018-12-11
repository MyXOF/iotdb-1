package cn.edu.tsinghua.iotdb.queryV2.engine.reader;

import cn.edu.tsinghua.iotdb.read.TimeValuePairReader;
import cn.edu.tsinghua.iotdb.utils.TimeValuePair;

import java.io.IOException;


public class PriorityTimeValuePairReader implements TimeValuePairReader {

    protected TimeValuePairReader seriesReader;
    protected Priority priority;

    public PriorityTimeValuePairReader(TimeValuePairReader seriesReader, Priority priority) {
        this.seriesReader = seriesReader;
        this.priority = priority;
    }

    @Override
    public boolean hasNext() throws IOException {
        return seriesReader.hasNext();
    }

    @Override
    public TimeValuePair next() throws IOException {
        return seriesReader.next();
    }

    @Override
    public void skipCurrentTimeValuePair() throws IOException {
        seriesReader.next();
    }

    @Override
    public void close() throws IOException {
        seriesReader.close();
    }

    public Priority getPriority() {
        return priority;
    }

    public static class Priority implements Comparable<Priority> {

        private int priority;

        public Priority(int priority) {
            this.priority = priority;
        }

        @Override
        public int compareTo(Priority o) {
            return this.priority - o.priority;
        }
    }
}
