package cn.edu.tsinghua.tsfile.timeseries.read.query.dataset;

import cn.edu.tsinghua.tsfile.common.exception.UnSupportedDataTypeException;
import cn.edu.tsinghua.tsfile.common.utils.Binary;
import cn.edu.tsinghua.tsfile.file.metadata.enums.TSDataType;
import cn.edu.tsinghua.tsfile.timeseries.read.common.Path;
import cn.edu.tsinghua.tsfile.timeseries.read.datatype.*;
import cn.edu.tsinghua.tsfile.timeseries.read.query.timegenerator.TimestampGenerator;
import cn.edu.tsinghua.tsfile.timeseries.read.reader.impl.SeriesReaderByTimestamp;

import java.io.IOException;
import java.util.List;


/**
 * query processing:
 *
 *   (1) generate time by series that has filter
 *   (2) get value of series that does not have filter
 *   (3) construct RowRecord
 */
public class DataSetWithTimeGenerator extends QueryDataSet {

    private TimestampGenerator timestampGenerator;
    private List<SeriesReaderByTimestamp> readers;
    private List<Boolean> cached;

    public DataSetWithTimeGenerator(List<Path> paths, List<Boolean> cached, List<TSDataType> dataTypes, TimestampGenerator timestampGenerator, List<SeriesReaderByTimestamp> readers) {
        super(paths, dataTypes);
        this.cached = cached;
        this.timestampGenerator = timestampGenerator;
        this.readers = readers;
    }


    @Override
    public boolean hasNext() throws IOException {
        return timestampGenerator.hasNext();
    }


    @Override
    public RowRecord next() throws IOException {
        long timestamp = timestampGenerator.next();
        RowRecord rowRecord = new RowRecord(timestamp);

        for (int i = 0; i < paths.size(); i++) {

            // get value from readers in time generator
            if (cached.get(i)) {
                Object value = timestampGenerator.getValue(paths.get(i), timestamp);
                rowRecord.addField(getField(value, dataTypes.get(i)));
                continue;
            }

            // get value from series reader without filter
            SeriesReaderByTimestamp seriesReaderByTimestamp = readers.get(i);
            Object value = seriesReaderByTimestamp.getValueInTimestamp(timestamp);
            rowRecord.addField(getField(value, dataTypes.get(i)));
        }

        return rowRecord;
    }

    private Field getField(Object value, TSDataType dataType) {
        Field field = new Field(dataType);

        if (value == null) {
            field.setNull();
            return field;
        }
        switch (dataType) {
            case DOUBLE:
                field.setDoubleV((double) value);
                break;
            case FLOAT:
                field.setFloatV((float) value);
                break;
            case INT64:
                field.setLongV((long) value);
                break;
            case INT32:
                field.setIntV((int) value);
                break;
            case BOOLEAN:
                field.setBoolV((boolean) value);
                break;
            case TEXT:
                field.setBinaryV((Binary) value);
                break;
            default:
                throw new UnSupportedDataTypeException("UnSupported" + String.valueOf(dataType));
        }
        return field;
    }
}
