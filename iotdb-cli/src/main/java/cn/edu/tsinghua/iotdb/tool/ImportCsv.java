package cn.edu.tsinghua.iotdb.tool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cn.edu.tsinghua.iotdb.jdbc.TsfileConnection;
import cn.edu.tsinghua.iotdb.jdbc.TsfileJDBCConfig;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.thrift.TException;

import cn.edu.tsinghua.iotdb.exception.ArgsErrorException;
import jline.console.ConsoleReader;

/**
 * CSV File To IoTDB
 *
 * @author zhanggr
 */
public class ImportCsv extends AbstractCsvTool{
    private static final String FILE_ARGS = "f";
    private static final String FILE_NAME = "file or folder";
    private static final String FILE_SUFFIX = "csv";

    private static final String TSFILEDB_CLI_PREFIX = "ImportCsv";
    private static final String ERROR_INFO_STR = "csvInsertError.error";

    private static final String STRING_DATA_TYPE = "TEXT";
    private static final int BATCH_EXECUTE_COUNT = 10;

    private static String filename;
    private static String errorInsertInfo = "";

    /**
     * commandline option create
     *
     * @return object Options
     */
    private static Options createOptions() {
        Options options = new Options();

        Option opHost = Option.builder(HOST_ARGS).longOpt(HOST_NAME).required().argName(HOST_NAME).hasArg().desc("Host Name (required)").build();
        options.addOption(opHost);

        Option opPort = Option.builder(PORT_ARGS).longOpt(PORT_NAME).required().argName(PORT_NAME).hasArg().desc("Port (required)").build();
        options.addOption(opPort);

        Option opUsername = Option.builder(USERNAME_ARGS).longOpt(USERNAME_NAME).required().argName(USERNAME_NAME).hasArg().desc("Username (required)").build();
        options.addOption(opUsername);

        Option opPassword = Option.builder(PASSWORD_ARGS).longOpt(PASSWORD_NAME).optionalArg(true).argName(PASSWORD_NAME).hasArg().desc("Password (optional)").build();
        options.addOption(opPassword);

        Option opFile = Option.builder(FILE_ARGS).required().argName(FILE_NAME).hasArg().desc("If input a file path, load a csv file, otherwise load all csv file under this directory (required)").build();
        options.addOption(opFile);

        Option opHelp = Option.builder(HELP_ARGS).longOpt(HELP_ARGS).hasArg(false).desc("Display help information").build();
        options.addOption(opHelp);
        
		Option opTimeZone = Option.builder(TIME_ZONE_ARGS).argName(TIME_ZONE_NAME).hasArg().desc("Time Zone eg. +08:00 or -01:00 (optional)").build();
		options.addOption(opTimeZone);
		
        return options;
    }

    /**
     * Data from csv To tsfile
     */
    private static void loadDataFromCSV(File file, int index) {
        Statement statement = null;
        BufferedReader br = null;
        BufferedWriter bw = null;
        File errorFile = new File(errorInsertInfo+index);
        boolean errorFlag = true;
        try {
            br = new BufferedReader(new FileReader(file));
            if(!errorFile.exists()) errorFile.createNewFile();
            bw = new BufferedWriter(new FileWriter(errorFile));
            
            String line = "";
            String header = br.readLine();
            String[] strHeadInfo = header.split(",");
            
            bw.write("From " + file.getAbsolutePath());
            bw.newLine();
            bw.newLine();
            bw.write(header);
            bw.newLine();
            bw.newLine();
     
            Map<String, ArrayList<Integer>> deviceToColumn = new HashMap<>(); // storage csv table head info
            List<String> colInfo = new ArrayList<>(); // storage csv table head info
            List<String> headInfo = new ArrayList<>(); // storage csv device sensor info, corresponding csv table head

            if (strHeadInfo.length <= 1) {
                System.out.println("[ERROR] The CSV file" + file.getName() + " illegal, please check first line");
                return;
            }
            
            long startTime = System.currentTimeMillis();
            Map<String, String> timeseriesDataType = new HashMap<>();
            DatabaseMetaData databaseMetaData = connection.getMetaData();

            for (int i = 1; i < strHeadInfo.length; i++) {
                ResultSet resultSet = databaseMetaData.getColumns(null, null, strHeadInfo[i], null);
                if (resultSet.next()) {
                    timeseriesDataType.put(resultSet.getString(1), resultSet.getString(2));
                } else {
                		String errorInfo = String.format("[ERROR] Database cannot find %s in %s, stop import!", strHeadInfo[i], file.getAbsolutePath());
                    System.out.println(errorInfo);
                    bw.write(errorInfo);
                    errorFlag = false;
                    return;
                }
                headInfo.add(strHeadInfo[i]);
                String deviceInfo = strHeadInfo[i].substring(0, strHeadInfo[i].lastIndexOf("."));

                if (!deviceToColumn.containsKey(deviceInfo)) {
                    deviceToColumn.put(deviceInfo, new ArrayList<>());
                }
                // storage every device's sensor index info
                deviceToColumn.get(deviceInfo).add(i - 1);
                colInfo.add(strHeadInfo[i].substring(strHeadInfo[i].lastIndexOf(".") + 1));
            }

            statement = connection.createStatement();
            int count = 0;
            List<String > tmp = new ArrayList<>();
            while ((line = br.readLine()) != null) {
            		List<String> sqls = new ArrayList<>(); 
            		try {
                        sqls = createInsertSQL(line, timeseriesDataType, deviceToColumn, colInfo, headInfo);
					} catch (Exception e) {
						bw.write(String.format("error input line, maybe it is not complete: %s", line));
						bw.newLine();
						errorFlag = false;
				}
                for (String str : sqls) {
                    try {
                        count++;
                        statement.addBatch(str);
                        tmp.add(str);
                        if (count == BATCH_EXECUTE_COUNT) {
                            int[] result = statement.executeBatch();
                            for(int i = 0; i < result.length;i++){
                            		if(result[i] != Statement.SUCCESS_NO_INFO && i < tmp.size()){
                            			bw.write(tmp.get(i));
                            			bw.newLine();
                            			errorFlag = false;
                            		}
                            }
                            statement.clearBatch();
                            count = 0;
                            tmp.clear();
                        }
                    } catch (SQLException e) {
                        bw.write(e.getMessage());
                        bw.newLine();
                        errorFlag = false;
                    }
                }
            }
            try {
            		int[] result = statement.executeBatch();
            		for(int i = 0; i < result.length;i++){
                		if(result[i] != Statement.SUCCESS_NO_INFO && i < tmp.size()){
                			bw.write(tmp.get(i));
                			bw.newLine();
                			errorFlag = false;
                		}
                }
            		statement.clearBatch();
            		count = 0;
                tmp.clear();
                System.out.println(String.format("[INFO] Load data from %s successfully, it takes %dms", file.getName(), (System.currentTimeMillis()-startTime)));
            } catch (SQLException e) {
                bw.write(e.getMessage());
                bw.newLine();
                errorFlag = false;
            }

        } catch (FileNotFoundException e) {
            System.out.println("[ERROR] Cannot find " + file.getName());
        } catch (IOException e) {
            System.out.println("[ERROR] CSV file read exception!" + e.getMessage());
        } catch (SQLException e) {
            System.out.println("[ERROR] Database connection exception!" + e.getMessage());
        } finally {
            try {
            		if(br != null) br.close();
            		if(bw != null) bw.close();
            		if(statement != null) statement.close();
            		if(errorFlag){
            			FileUtils.forceDelete(errorFile);
            		} else{
            			System.out.println(String.format("[ERROR] Format of some lines in %s error, please check %s for more information", file.getAbsolutePath(), errorFile.getAbsolutePath()));
            		}
            } catch (SQLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param line csv line data
     * @param timeseriesToType
     * @param deviceToColumn
     * @param colInfo
     * @param headInfo
     * @return
     * @throws IOException
     */
    private static List<String> createInsertSQL(String line,  Map<String, String> timeseriesToType,
    		Map<String, ArrayList<Integer>> deviceToColumn, List<String> colInfo, List<String> headInfo) throws IOException {
        String[] data = line.split(",", headInfo.size() + 1);
        List<String> sqls = new ArrayList<>();
        Iterator<Map.Entry<String, ArrayList<Integer>>> it = deviceToColumn.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ArrayList<Integer>> entry = it.next();
            StringBuilder sbd = new StringBuilder();
            ArrayList<Integer> colIndex = entry.getValue();
            sbd.append("insert into " + entry.getKey() + "(timestamp");
            int skipcount = 0;
            for (int j = 0; j < colIndex.size(); ++j) {
                if (data[entry.getValue().get(j) + 1].equals("")) {
                    skipcount++;
                    continue;
                }
                sbd.append(", " + colInfo.get(colIndex.get(j)));
            }
            // define every device null value' number, if the number equal the
            // sensor number, the insert operation stop
            if (skipcount == entry.getValue().size())
                continue;
            
            // TODO when timestampsStr is empty, 
            String timestampsStr = data[0];
            sbd.append(") values(").append(timestampsStr.trim().equals("") ? "NO TIMESTAMP" : timestampsStr);
//            if (timestampsStr.trim().equals("")) {
//                continue;
//            }
//            sbd.append(") values(").append(timestampsStr);

            for (int j = 0; j < colIndex.size(); ++j) {
                if (data[entry.getValue().get(j) + 1].equals(""))
                    continue;
                if (timeseriesToType.get(headInfo.get(colIndex.get(j))).equals(STRING_DATA_TYPE)) {
                    sbd.append(", \'" + data[colIndex.get(j) + 1] + "\'");
                } else {
                    sbd.append("," + data[colIndex.get(j) + 1]);
                }
            }
            sbd.append(")");
            sqls.add(sbd.toString());
        }
        return sqls;
    }

    public static void main(String[] args) throws IOException, SQLException {
        Options options = createOptions();
        HelpFormatter hf = new HelpFormatter();
        hf.setOptionComparator(null);
        hf.setWidth(MAX_HELP_CONSOLE_WIDTH);
        CommandLine commandLine = null;
        CommandLineParser parser = new DefaultParser();

		if (args == null || args.length == 0) {
			System.out.println("[ERROR] Too few params input, please check the following hint.");
			hf.printHelp(TSFILEDB_CLI_PREFIX, options, true);
			return;
		}
		try {
			commandLine = parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			hf.printHelp(TSFILEDB_CLI_PREFIX, options, true);
			return;
		}	
        if (commandLine.hasOption(HELP_ARGS)) {
            hf.printHelp(TSFILEDB_CLI_PREFIX, options, true);
            return;
        }
        
        ConsoleReader reader = new ConsoleReader();
		reader.setExpandEvents(false);
		try {
			parseBasicParams(commandLine, reader);
            filename = commandLine.getOptionValue(FILE_ARGS);
            if (filename == null) {
                hf.printHelp(TSFILEDB_CLI_PREFIX, options, true);
                return;
            }
			parseSpecialParams(commandLine, reader);
	        importCsvFromFile(host, port, username, password, filename, timeZoneID);
		} catch (ArgsErrorException e) { 		
		} catch (Exception e) {
			System.out.println(String.format("[ERROR] Encounter an error, because %s", e.getMessage()));
		} finally {
            reader.close();
        }
    }

    private static void parseSpecialParams(CommandLine commandLine, ConsoleReader reader) throws IOException, ArgsErrorException {
		timeZoneID = commandLine.getOptionValue(TIME_ZONE_ARGS);
	}

    public static void importCsvFromFile(String ip,String port, String username, String password, String filename, String timeZone) throws SQLException{
		String property = System.getProperty("IOTDB_HOME");
		if (property == null) {
			errorInsertInfo = ERROR_INFO_STR;
		} else {
			errorInsertInfo = property + File.separatorChar + ERROR_INFO_STR;
		}
    		try {
    			Class.forName(TsfileJDBCConfig.JDBC_DRIVER_NAME);
    			connection = (TsfileConnection) DriverManager.getConnection("jdbc:tsfile://" + ip + ":" + port + "/", username, password);
    			timeZoneID = timeZone;
    			setTimeZone();
    			
    	        File file = new File(filename);
    	        if(file.isFile()){
    	        		if(file.getName().endsWith(FILE_SUFFIX)){
    		        		loadDataFromCSV(file, 1);
    	        		} else{
    	        			System.out.println("[WARN] File "+file.getName()+" should ends with '.csv' if you want to import");	        			
    	        		}
    	        } else if(file.isDirectory()){
    	        		int i = 1;
    	        		for(File f : file.listFiles()){
    	        			if(f.isFile()) {
    	        				if(f.getName().endsWith(FILE_SUFFIX)){
    		        				loadDataFromCSV(f, i);
    		        				i++;
    	        				} else{
    	    	        				System.out.println("[WARN] File "+f.getName()+" should ends with '.csv' if you want to import");	        			
    	    	        			}
    	        			}
    	        		}
    	        }
		} catch (ClassNotFoundException e) {
			System.out.println("[ERROR] Failed to dump data because cannot find TsFile JDBC Driver, please check whether you have imported driver or not");		
		} catch (TException e) {
			System.out.println(String.format("[ERROR] Encounter an error when connecting to server, because %s", e.getMessage()));
		} catch (Exception e) {
			System.out.println(String.format("[ERROR] Encounter an error, because %s", e.getMessage()));
		} finally {
			if (connection != null){
				connection.close();
			}
		}

    }
}
