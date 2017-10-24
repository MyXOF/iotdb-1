package cn.edu.tsinghua.tsfile.timeseries.read;

import cn.edu.tsinghua.tsfile.common.utils.ITsRandomAccessFileReader;
import cn.edu.tsinghua.tsfile.file.metadata.RowGroupMetaData;
import cn.edu.tsinghua.tsfile.file.metadata.enums.TSDataType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jinrui Zhang
 * This class is used to construct FileReader. <br>
 * It is an adapter between {@code RecordReader} and {@code FileReader}
 */
public class ReaderManager {

    private FileReader fileReader;
    private ITsRandomAccessFileReader raf;

    private List<FileReader> fileReaderList;
    private List<ITsRandomAccessFileReader> rafList;
    private HashMap<String, List<RowGroupReader>> rowGroupReaderMap;
    private List<RowGroupReader> rowGroupReaderList;

    public ReaderManager(ITsRandomAccessFileReader raf) throws IOException {
        this.raf = raf;
        rowGroupReaderList = new ArrayList<>();
        rowGroupReaderMap = new HashMap<>();

        fileReader = new FileReader(raf);
        addRowGroupReadersToMap(fileReader);
        addRowGroupReadersToList(fileReader);
    }

    /**
     * @param rafList fileInputStreamList
     * @throws IOException exception in IO
     */
    public ReaderManager(List<ITsRandomAccessFileReader> rafList) throws IOException {
        this.rafList = rafList;
        rowGroupReaderList = new ArrayList<>();
        rowGroupReaderMap = new HashMap<>();
        fileReaderList = new ArrayList<>();

        for (int i = 0; i < rafList.size(); i++) {
            FileReader fr = new FileReader(rafList.get(i));
            fileReaderList.add(fr);
            addRowGroupReadersToMap(fr);
            addRowGroupReadersToList(fr);
        }
    }

    /**
     * @param rafList list of TSRandomAccessFileReader
     * @param unenvelopedFileReader fileReader for unenvelopedFile
     * @param rowGroupMetadataList  RowGroupMetadata List for unenvelopedFile
     * @throws IOException exception in IO
     */
    public ReaderManager(List<ITsRandomAccessFileReader> rafList,
                         ITsRandomAccessFileReader unenvelopedFileReader, List<RowGroupMetaData> rowGroupMetadataList) throws IOException {
        this(rafList);
        this.rafList.add(unenvelopedFileReader);

        FileReader fr = new FileReader(unenvelopedFileReader, rowGroupMetadataList);
        addRowGroupReadersToMap(fr);
        addRowGroupReadersToList(fr);
    }

    private void addRowGroupReadersToMap(FileReader fileReader) {
        HashMap<String, ArrayList<RowGroupReader>> rgrMap = fileReader.getRowGroupReadersMap();
        for (String deltaObjectUID : rgrMap.keySet()) {
            if (rowGroupReaderMap.containsKey(deltaObjectUID)) {
                rowGroupReaderMap.get(deltaObjectUID).addAll(rgrMap.get(deltaObjectUID));
            } else {
                rowGroupReaderMap.put(deltaObjectUID, rgrMap.get(deltaObjectUID));
            }
        }
    }

    private void addRowGroupReadersToList(FileReader fileReader) {
        this.rowGroupReaderList.addAll(fileReader.getRowGroupReaderList());
    }

    public List<RowGroupReader> getAllRowGroupReaders() {
        return rowGroupReaderList;
    }

    public List<RowGroupReader> getRowGroupReaderListByDeltaObject(String deltaObjectUID) {
        List<RowGroupReader> ret = rowGroupReaderMap.get(deltaObjectUID);
        if (ret == null) {
            return new ArrayList<RowGroupReader>();
        }
        return ret;
    }

    public RowGroupReader getRowGroupReader(String deltaObjectUID, int index) {
        return this.fileReader.getRowGroupReader(deltaObjectUID, index);
    }

    public ITsRandomAccessFileReader getInput() {
        return this.raf;
    }

    public TSDataType getDataTypeBySeriesName(String deltaObject, String measurement) {
        ArrayList<RowGroupReader> rgrlist = fileReader.getRowGroupReadersMap().get(deltaObject);
        if (rgrlist == null || rgrlist.size() == 0) {
            return null;
        }
        return rgrlist.get(0).getDataTypeBySeriesName(measurement);
    }

    public HashMap<String, List<RowGroupReader>> getRowGroupReaderMap() {
        return rowGroupReaderMap;
    }

    public Map<String, String> getProps() {
        return fileReader.getProps();
    }

    public String getProp(String key) {
        return fileReader.getProp(key);
    }

    /**
     *
     * @throws IOException exception in IO
     */
    public void close() throws IOException {
        for (ITsRandomAccessFileReader raf : rafList) {
            if (raf instanceof TsRandomAccessLocalFileReader) {
                ((TsRandomAccessLocalFileReader) raf).closeFromManager();
            } else {
                raf.close();
            }
        }
    }
}
