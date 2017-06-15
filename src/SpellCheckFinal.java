import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.xml.sax.SAXException;

public class SpellCheckFinal
{
  public static HashMap<String, ArrayList<String>> kw_permutations = new HashMap();
  public static long startTime = System.currentTimeMillis();
  public static BufferedReader cleaningList = null;
  public static BufferedReader cleaningListAlt = null;
  public static ArrayList<String> cleaningList_ = new ArrayList();
  public static ArrayList<String> cleaningList_alt = new ArrayList();
  public static HashMap<String, String> descList = new HashMap();
  public static HashMap<String, HashMap<Integer, String>> skillList = new HashMap();
  public static XSSFWorkbook wb = new XSSFWorkbook();
  public static XSSFSheet skillSheet = wb.createSheet("SkillData");
  public static int rowCount = 1;
  public static Row headerRow = skillSheet.createRow(0);
  
  public static void main(String[] args)
    throws IOException, SAXException, ClassNotFoundException, SQLException
  {
    Long init = Long.valueOf(System.currentTimeMillis());
    System.getProperty("file.encoding");
    System.setProperty("file.encoding", "UTF-8");
    MongoCredential credential = MongoCredential.createCredential("admin", "admin", "HRPublicSpiel".toCharArray());
    MongoClient mongoClient = new MongoClient(new ServerAddress(), Arrays.asList(new MongoCredential[] { credential }));
    //MongoClient mongoClient = new MongoClient(new ServerAddress("localhost", 27017));
    DB db = mongoClient.getDB("db_jobs_new");
    DBCollection bas_job = db.getCollection("bas_job_data_ext");
    BasicDBObject query = new BasicDBObject();
    query.put("craCountryCode", "HU");
    //query.put("craLangCodeDescription", "en");
    BasicDBObject fields = new BasicDBObject();
    fields.put("craLocalJobID", Integer.valueOf(1));
    fields.put("craCompanyName", Integer.valueOf(1));
    fields.put("craJobDescription", Integer.valueOf(1));
    DBCursor cursor = bas_job.find(query, fields);
    while (cursor.hasNext()) {
      DBObject document = cursor.next();
      descList.put(document.get("craLocalJobID").toString(), document.get("craJobDescription").toString());
    }
    cleaningList = new BufferedReader(new FileReader("/root/hassaan/spellCheck/cleaningList.txt"));
    cleaningListAlt = new BufferedReader(new FileReader("/root/hassaan/spellCheck/cleaningList_alt.txt"));
    //cleaningList = new BufferedReader(new FileReader("C:\\Users\\sakshi\\Downloads\\cleaningList.txt"));
    //cleaningListAlt = new BufferedReader(new FileReader("C:\\Users\\sakshi\\Downloads\\cleaningList_alt.txt"));
    for (String temp = ""; temp != null; temp = cleaningList.readLine()) {
      cleaningList_.add(temp);
    }
    for (String temp = ""; temp != null; temp = cleaningListAlt.readLine()) {
      cleaningList_alt.add(temp);
    }
    DBCollection dem_skill = db.getCollection("dem_skill");
    BasicDBObject field = new BasicDBObject();
    field.put("sllSkillCatalogueLevelID_fk", Integer.valueOf(-1));
    DBCursor cursor_dem_skill = dem_skill.find().sort(field).limit(1);
    int max = 0;
    while (cursor_dem_skill.hasNext()) {
      DBObject document_dem_skill = cursor_dem_skill.next();
      max = Math.round(Float.parseFloat(document_dem_skill.get("sllSkillCatalogueLevelID_fk").toString()));
    }
    dem_skill = db.getCollection("dem_skill");
    BasicDBObject query_dem_skill = new BasicDBObject();
    query_dem_skill.put("sllSkillCatalogueLevelID_fk", Integer.valueOf(max));
    DBCursor cursor_skill = dem_skill.find(query_dem_skill);
    Map newMap = new HashMap();
    int count = 0;
    String skillNew;
    while (cursor_skill.hasNext()) {
      DBObject document_skill = cursor_skill.next();
      count++;
      int skillId = Math.round(Float.parseFloat(document_skill.get("sllSkillID").toString()));
      String skillName = document_skill.get("sllIdentifierName").toString();
      skillNew = skillName.replace("||", "");
      newMap.put(Integer.valueOf(skillId), skillNew);
    }
    createMutations(newMap.values());
    doFurtherProcessing(newMap);
    
    DBCollection lnk_basjobdataext_dem_skill = db.getCollection("Lnk_basjobdataext_dem_skill");
    for (Entry entry : skillList.entrySet()) {
    	//entry = (Map.Entry)skillId.next();     
      for (Entry subentry : ((HashMap<Integer, String>) entry.getValue()).entrySet()) {
        DBObject lnk_doc = new BasicDBObject();
        lnk_doc.put(entry.getKey().toString().replace(".", ""), subentry.getKey());
        lnk_basjobdataext_dem_skill.insert(new DBObject[] { lnk_doc });
      }
    }
    Long fin = Long.valueOf(System.currentTimeMillis());
    System.out.println("Job completed. Time taken is: " + Long.toString((fin.longValue() - init.longValue()) / 1000L) + "seconds");
  }
  
  private static void doFurtherProcessing(Map map) throws IOException, SAXException, SQLException
  {
    System.out.println("Inside further processing:" + descList.size());
    Cell nameHeaderCell0 = headerRow.createCell(0);
    nameHeaderCell0.setCellValue("Original Description");
    Cell nameHeaderCell1 = headerRow.createCell(1);
    nameHeaderCell1.setCellValue("Processed Description");
    Cell nameHeaderCell2 = headerRow.createCell(2);
    nameHeaderCell2.setCellValue("Extracted Skills Id");
    Cell nameHeaderCell3 = headerRow.createCell(3);
    nameHeaderCell3.setCellValue("Extracted Skills ");
    
    int rowCount = 1;
    for (Map.Entry entry : descList.entrySet())
    {
      rowCount+=1;
      if (rowCount%10 == 0) {
    	  System.out.println(rowCount);
      }
      String descCleaned = descriptionFormatter(entry.getValue().toString());
      HashMap<Integer, String> skills = extractSkills(descCleaned, map);
      writeExcel(entry.getValue().toString(), descCleaned, skills);
      skillList.put(entry.getKey().toString(), skills);
    }
    String outputDirPath = "/root/hassaan/spellCheck/SkillList_HU_1.xls";
    //String outputDirPath = "C:\\Users\\sakshi\\DownloadsSkillList.xls";
    FileOutputStream fileOut = new FileOutputStream(outputDirPath);
    wb.write(fileOut);
    fileOut.flush();
    fileOut.close();
  }
  
  private static void createMutations(Collection collection) throws IOException {
    for (Iterator iterator = collection.iterator(); iterator.hasNext();) {
      String str = (String)iterator.next();
      String[] splitStr = str.split(" ");
      for (String st : splitStr) {
        String patternStr = "[\\–,\\_\\[\\]\\,\\(\\)\\|\\\"\\\\}\\{]+";
        st = st.replaceAll(patternStr, "");
        createKWmutations(st);
      }
    }
  }
  
  private static void createKWmutations(String word) {
    ArrayList<String> strList = new ArrayList<String>();
    // All deletes of a single letter
    for (int i = 0; i < word.length(); ++i) {
      strList.add(word.substring(0, i).toLowerCase() + word.substring(i + 1).toLowerCase());
    }
    // All swaps of adjacent letters
    for (int i = 0; i < word.length() - 1; ++i) {
      strList.add(word.substring(0, i).toLowerCase() + word.substring(i + 1, i + 2).toLowerCase() + word
        .substring(i, i + 1).toLowerCase() + word.substring(i + 2).toLowerCase());
    }
	// All replacements of a letter
    for (int i = 0; i < word.length(); i++) {
      for (char c = 'a'; c <= 'z'; ++c)
        strList.add(word.substring(0, i).toLowerCase() + String.valueOf(c).toLowerCase() + word.substring(i + 1).toLowerCase());
    }
    for (int i = 0; i <= word.length(); i++)
      for (char c = 'a'; c <= 'z'; ++c)
        strList.add(word.substring(0, i).toLowerCase() + String.valueOf(c).toLowerCase() + word.substring(i).toLowerCase());
    kw_permutations.put(word.toLowerCase(), strList);
    return;
  }
  

  private static String descriptionFormatter(String desc) throws IOException {
	int matchfound;
    if (kw_permutations.keySet().contains("")) {
      kw_permutations.remove("");
    }
    String str_init = desc;
    String[] str_split = desc.split(" ");
    for (String substr : str_split) {
      if (substr.length() > 3) {
      matchfound = 0;
      for (int j = 1; j < cleaningList_alt.size(); j++) {
        String str_temp = (String)cleaningList_alt.get(j);
        if (substr.contains(str_temp)) {
          substr = substr.replace(str_temp, "");
        }
      }
      if ((!substr.equals("")) && 
        (!kw_permutations.keySet().contains(substr.toLowerCase())))
      {


        boolean pointer = false;
        for (int j = 1; j < cleaningList_.size(); j++) {
          String str_temp = (String)cleaningList_.get(j);
          if (substr.equalsIgnoreCase(str_temp)) {
            pointer = true;
            break;
          }
        }
        if (!pointer) {
          for (Map.Entry<String, ArrayList<String>> entry : kw_permutations.entrySet()) {
            if (((ArrayList)entry.getValue()).contains(substr.toLowerCase())) {
              desc = desc.replace(substr, (CharSequence)entry.getKey());
            }
          }
        }
      }
    }
  }
  return desc;
  }
  
  private static HashMap<Integer, String> extractSkills(String inputText, Map map) {
    StringBuilder builder = new StringBuilder();
    HashMap<Integer, String> idvalue = new HashMap();
    String patternStr = "[-_[],.|\"\\(){}\\u2022\\u2023\\u25E6\\u2043\\u2219]]";
    String mcstr = inputText.replaceAll(patternStr, "");
    Iterator entries = map.entrySet().iterator();
    while (entries.hasNext()) {
      Map.Entry entry = (Map.Entry)entries.next();
      String value = (String)entry.getValue();
      StringBuilder sb = new StringBuilder();
      if ((value.contains("(")) && (!value.contains(")"))) {
        value = value.concat(")");
        sb = sb.append(value);
      } else {
        sb = sb.append(value);
      }
      int id = 0;
      /* sb is the skill to be checked and if it is originally in uppercase, compare it with description as it is,
      	 else check the lower-case version of sb with lower case version of description to check its presence.
      */
      if (sb.toString().equals(sb.toString().toUpperCase())) {
    	  if ((mcstr.startsWith(sb.toString().concat(" "))) || 
    		        (mcstr.startsWith(sb.toString().concat("/")))) {
    		        id = ((Integer)entry.getKey()).intValue();
    		        idvalue.put(Integer.valueOf(id), sb.toString().concat("|").concat(mcstr));
    		      } else if ((mcstr.endsWith(" ".concat(sb.toString()))) || 
    		        (mcstr.endsWith("/".concat(sb.toString())))) {
    		        id = ((Integer)entry.getKey()).intValue();
    		        idvalue.put(Integer.valueOf(id), sb.toString().concat("|").concat(mcstr));
    		      } else if ((mcstr.contains(" ".concat(sb.toString()).concat(" "))) || 
    		        (mcstr.toLowerCase().contains("/".concat(sb.toString()).concat("/"))) || 
    		        (mcstr.toLowerCase().contains(" ".concat(sb.toString()).concat("/"))) || 
    		        (mcstr.toLowerCase().contains("/".concat(sb.toString()).concat(" ")))) {
    		        id = ((Integer)entry.getKey()).intValue();
    		        idvalue.put(Integer.valueOf(id), sb.toString().concat("|").concat(mcstr));
    		      } else if (mcstr.equals(sb.toString())) {
    		        id = ((Integer)entry.getKey()).intValue();
    		        idvalue.put(Integer.valueOf(id), sb.toString().concat("|").concat(mcstr));
    		      }
      }
      else {
	      if ((mcstr.toLowerCase().startsWith(sb.toString().toLowerCase().concat(" "))) || 
	        (mcstr.toLowerCase().startsWith(sb.toString().toLowerCase().concat("/")))) {
	        id = ((Integer)entry.getKey()).intValue();
	        idvalue.put(Integer.valueOf(id), sb.toString().concat("|").concat(mcstr));
	      } else if ((mcstr.toLowerCase().endsWith(" ".concat(sb.toString().toLowerCase()))) || 
	        (mcstr.toLowerCase().endsWith("/".concat(sb.toString().toLowerCase())))) {
	        id = ((Integer)entry.getKey()).intValue();
	        idvalue.put(Integer.valueOf(id), sb.toString().concat("|").concat(mcstr));
	      } else if ((mcstr.toLowerCase().contains(" ".concat(sb.toString().toLowerCase()).concat(" "))) || 
	        (mcstr.toLowerCase().contains("/".concat(sb.toString().toLowerCase()).concat("/"))) || 
	        (mcstr.toLowerCase().contains(" ".concat(sb.toString().toLowerCase()).concat("/"))) || 
	        (mcstr.toLowerCase().contains("/".concat(sb.toString().toLowerCase()).concat(" ")))) {
	        id = ((Integer)entry.getKey()).intValue();
	        idvalue.put(Integer.valueOf(id), sb.toString().concat("|").concat(mcstr));
	      } else if (mcstr.toLowerCase().equals(sb.toString().toLowerCase())) {
	        id = ((Integer)entry.getKey()).intValue();
	        idvalue.put(Integer.valueOf(id), sb.toString().concat("|").concat(mcstr));
	      }
      }
    }
    
    return idvalue;
  }
  
  private static void writeExcel(String desc, String descCleaned, HashMap<Integer, String> skills)
    throws SQLException, IOException
  {
    Row dataRow = skillSheet.createRow(rowCount);
    Cell datasIdCell0 = dataRow.createCell(0);
    datasIdCell0.setCellValue(desc);
    Cell dataIdCell1 = dataRow.createCell(1);
    dataIdCell1.setCellValue(descCleaned);
    Cell dataIdCell2 = dataRow.createCell(2);
    
    StringBuilder builder = new StringBuilder();
    ArrayList<Integer> skillIds = new ArrayList<Integer>();
    for (Map.Entry<Integer, String> entry : skills.entrySet())
    {
      if (!skillIds.contains(entry.getKey())){
    	  skillIds.add(entry.getKey());
      }
    }
    for (int skillId : skillIds)
    {
  	  builder.append(skillId).append("||"); 	
    }
    
    dataIdCell2.setCellValue(builder.toString());
    Cell dataIdCell3 = dataRow.createCell(3);
    StringBuilder builder1 = new StringBuilder();
    ArrayList<String> skillNames = new ArrayList<String>();
    for (Map.Entry<Integer, String> entry : skills.entrySet())
    {
        if (!skillNames.contains(entry.getValue())){
        	skillNames.add(entry.getValue());
        }
    }
    for (String skillName : skillNames)
    {
        builder1.append((skillName).split("\\|")[0]).append("||");	
    }
    dataIdCell3.setCellValue(builder1.toString());
    

    rowCount += 1;
  }
}
