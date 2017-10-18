package tools;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;

public class GradeHistoryScraper {
    public static void main(String[] args) throws Exception {
        try (final WebClient webClient = new WebClient()) {
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            webClient.getOptions().setThrowExceptionOnScriptError(false);

            HtmlPage page = webClient.getPage("http://web-as.tamu.edu/gradereport/");

            HtmlSelect yearSelect = (HtmlSelect) page.getElementById("ctl00_plcMain_lstGradYear");
            HtmlSelect collegeSelect = (HtmlSelect) page.getElementById("ctl00_plcMain_lstGradCollege");

            for (HtmlOption yearOption : yearSelect.getOptions()) {
                int year = Integer.parseInt(yearOption.getValueAttribute());
//                if (year >= 2009) {
                if (year == 2016) {
//                    for (int semester = 1; semester <= 3; semester++) {
                    {
                        int semester = 1;

                        JSONObject termGrades = new JSONObject();

                        for (HtmlOption collegeOption : collegeSelect.getOptions()) {
                            String college = collegeOption.getValueAttribute();
                            if (!college.equals("UT")) { // skip university totals
                                String pdfFile = "http://web-as.tamu.edu/gradereport/PDFReports/" + year + semester + "/grd" + year + semester + college + ".pdf";
                                System.out.print(pdfFile);
                                try {
                                    String txtFile = downloadTxt(pdfFile);
                                    System.out.print(" > " + txtFile);
                                    JSONObject grades = makeJson(txtFile);

                                    String jsonFile = txtFile.replace("txt", "json");
                                    try (FileWriter file = new FileWriter(jsonFile)) {
                                        file.write(grades.toString());
                                    }
                                    System.out.print(" > " + jsonFile);

                                    mergeJson(termGrades, grades);
                                } catch (FileNotFoundException e) {
                                    System.out.print(" > not found");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                System.out.println();
                            }
                        }

                        System.out.println("POSTING TERM: " + year + semester);

                        WebRequest requestSettings = new WebRequest(new URL("https://aggie-scheduler.mybluemix.net/api/grades"), HttpMethod.POST);
//                        WebRequest requestSettings = new WebRequest(new URL("http://localhost:3000/api/grades"), HttpMethod.POST);

                        int counter = 0;
                        JSONObject termGradesSubset = new JSONObject();
                        for (String course : termGrades.keySet()) {
                            termGradesSubset.put(course, termGrades.getJSONObject(course));

                            if (++counter == 100) {
                                requestSettings.setRequestParameters(new ArrayList());
                                requestSettings.getRequestParameters().add(new NameValuePair("term", "" + year + semester));
                                requestSettings.getRequestParameters().add(new NameValuePair("termGrades", termGradesSubset.toString()));
                                webClient.getPage(requestSettings);

                                counter = 0;
                                termGradesSubset = new JSONObject();

                                Thread.sleep(1000);
                            }
                        }

                        if (counter > 0) {
                            requestSettings.setRequestParameters(new ArrayList());
                            requestSettings.getRequestParameters().add(new NameValuePair("term", "" + year + semester));
                            requestSettings.getRequestParameters().add(new NameValuePair("termGrades", termGradesSubset.toString()));
                            webClient.getPage(requestSettings);
                        }
                    }
                }
            }
        }
    }

    /*
     * Extracts the text from the pdf link provided and saves it to a .txt file
     * Returns the filepath of the .txt file that was generated
     */
    private static String downloadTxt(String pdfFile) throws Exception {
        Writer output = null;
        PDDocument document = null;
        String txtFile;
        try {
            URL url = new URL(pdfFile);
            document = PDDocument.load(url.openConnection().getInputStream());
            String fileName = url.getFile();
            File outputFile = new File(fileName.substring(0, fileName.length() - 4) + ".txt");
            txtFile = "data/grades/raw/" + outputFile.getName();
            output = new OutputStreamWriter(new FileOutputStream(txtFile), "UTF-8");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            stripper.writeText(document, output);
        } finally {
            try {
                if (output != null)
                    output.close();
                if (document != null)
                    document.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return txtFile;
    }

    /*
     * Given a filepath to a .txt file, extracts the information and stores it in JSON format
     * Returns a JSONObject of all grade data
     */
    private static JSONObject makeJson(String txtFile) throws Exception {
        JSONObject termObj = new JSONObject();
        File file = new File(txtFile);

        try {
            Scanner sc = new Scanner(file);

            if (txtFile.contains("SL")) { // File format 3 (School of Law)
                parseFormat3(termObj, sc);
            } else if (sc.findInLine("COLLEGE:") != null) { // File format 1
                sc = new Scanner(file); // start over
                parseFormat1(termObj, sc);
            } else if (sc.findInLine("SECTION") != null) { // File format 2
                sc = new Scanner(file); // start over
                parseFormat2(termObj, sc);
            } else {
                // this is odd
                System.out.println("Unrecognized file format: " + txtFile);
            }

            sc.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return termObj;
    }

    private static void parseFormat1(JSONObject termObj, Scanner sc) throws Exception {
        while (sc.hasNextLine()) {
            if (sc.findInLine("COLLEGE:") != null) {
                for (int i = 0; i < 10; i++) {
                    sc.nextLine();
                }
            } else if (sc.findInLine("COURSE TOTAL:") != null) {
                for (int i = 0; i < 2; i++) {
                    sc.nextLine();
                }
            } else if (sc.findInLine("DEPARTMENT TOTAL:") != null || sc.findInLine("COLLEGE TOTAL:") != null) {
                for (int i = 0; i < 3; i++) {
                    sc.nextLine();
                }
            } else {
                JSONObject gradeDist = new JSONObject();

                String[] courseArr = sc.next().split("-");
                String course = courseArr[0] + '-' + courseArr[1];
                String section = courseArr[2];
                sc.nextDouble(); // skip GPA
                String instructor = sc.nextLine().trim(); // whatever's left

                sc.nextLine(); // skip line 2

                gradeDist.put("a", sc.nextInt());
                gradeDist.put("b", sc.nextInt());
                gradeDist.put("c", sc.nextInt());
                gradeDist.put("d", sc.nextInt());
                gradeDist.put("f", sc.nextInt());
                gradeDist.put("totalAF", sc.nextInt());
                gradeDist.put("i", sc.nextInt());
                gradeDist.put("s", sc.nextInt());
                gradeDist.put("u", sc.nextInt());
                gradeDist.put("q", sc.nextInt());
                gradeDist.put("x", sc.nextInt());
                gradeDist.put("total", sc.nextInt());
                sc.nextLine(); // finish line

                sc.nextLine(); // skip line 4


                JSONObject courseObj;
                JSONObject instructorObj;
                JSONObject gradesObj;

                if (termObj.has(course)) {
                    courseObj = termObj.getJSONObject(course);
                } else {
                    courseObj = new JSONObject();
                }

                if (courseObj.has(instructor)) {
                    instructorObj = courseObj.getJSONObject((instructor));
                } else {
                    instructorObj = new JSONObject();
                }

                if (instructorObj.has(section)) {
                    System.out.println("WARNING: overwriting data for " + course + "-" + section + " (" + instructor + ")");
                }
                gradesObj = gradeDist;

                instructorObj.put(section, gradesObj);

                courseObj.put(instructor, instructorObj);

                termObj.put(course, courseObj);
            }
        }
    }

    private static void parseFormat2(JSONObject termObj, Scanner sc) throws Exception {
        while (sc.hasNextLine()) {
            if (sc.findInLine("SECTION") != null) {
                for (int i = 0; i < 38; i++) {
                    sc.nextLine();
                }
            } else if (sc.findInLine("COURSE TOTAL:") != null || sc.findInLine("DEPARTMENT TOTAL:") != null || sc.findInLine("COLLEGE TOTAL:") != null) {
                for (int i = 0; i < 11; i++) {
                    sc.nextLine();
                }
            } else {
                JSONObject gradeDist = new JSONObject();

                String[] courseArr = sc.next().split("-");
                String course = courseArr[0] + '-' + courseArr[1];
                String section = courseArr[2];
                gradeDist.put("a", sc.nextInt());
                sc.nextLine(); // finish line
                sc.nextLine(); // skip A%

                gradeDist.put("b", sc.nextInt());
                sc.nextLine(); // finish line
                sc.nextLine(); // skip B%

                gradeDist.put("c", sc.nextInt());
                sc.nextLine(); // finish line
                sc.nextLine(); // skip C%

                gradeDist.put("d", sc.nextInt());
                sc.nextLine(); // finish line
                sc.nextLine(); // skip D%

                gradeDist.put("f", sc.nextInt());
                sc.nextLine(); // finish line
                sc.nextLine(); // skip F%

                gradeDist.put("totalAF", sc.nextInt());
                sc.nextDouble(); // skip GPA
                gradeDist.put("i", sc.nextInt());
                gradeDist.put("s", sc.nextInt());
                gradeDist.put("u", sc.nextInt());
                gradeDist.put("q", sc.nextInt());
                gradeDist.put("x", sc.nextInt());
                gradeDist.put("total", sc.nextInt());
                String instructor = sc.nextLine().trim(); // whatever's left

                JSONObject courseObj;
                JSONObject instructorObj;
                JSONObject gradesObj;

                if (termObj.has(course)) {
                    courseObj = termObj.getJSONObject(course);
                } else {
                    courseObj = new JSONObject();
                }

                if (courseObj.has(instructor)) {
                    instructorObj = courseObj.getJSONObject((instructor));
                } else {
                    instructorObj = new JSONObject();
                }

                if (instructorObj.has(section)) {
                    System.out.println("WARNING: overwriting data for " + course + "-" + section + " (" + instructor + ")");
                }
                gradesObj = gradeDist;

                instructorObj.put(section, gradesObj);

                courseObj.put(instructor, instructorObj);

                termObj.put(course, courseObj);
            }
        }
    }

    private static void parseFormat3(JSONObject termObj, Scanner sc) throws Exception {
        while (sc.hasNextLine()) {
            if (sc.findInLine("COLLEGE:") != null) {
                for (int i = 0; i < 21; i++) {
                    sc.nextLine();
                }
            } else if (sc.findInLine("COURSE TOTAL:") != null || sc.findInLine("COLLEGE TOTAL:") != null) {
                for (int i = 0; i < 22; i++) {
                    sc.nextLine();
                }
            } else if (sc.findInLine("DEPARTMENT TOTAL:") != null) {
                for (int i = 0; i < 23; i++) {
                    sc.nextLine();
                }
            } else {
                JSONObject gradeDist = new JSONObject();
                int a = 0;
                int b = 0;
                int c = 0;
                int d = 0;

                String[] courseArr = sc.next().split("-");
                String course = courseArr[0] + '-' + courseArr[1];
                String section = courseArr[2];
                sc.nextDouble(); // skip GPA
                sc.nextLine(); // finish line

                String instructor = sc.nextLine().trim(); // whatever's left

                d += sc.nextInt(); // D+
                gradeDist.put("f", sc.nextInt());
                gradeDist.put("totalAF", sc.nextInt());
                gradeDist.put("i", sc.nextInt());
                gradeDist.put("s", sc.nextInt());
                gradeDist.put("u", sc.nextInt());
                gradeDist.put("q", sc.nextInt());
                gradeDist.put("x", sc.nextInt());
                gradeDist.put("total", sc.nextInt());
                sc.nextLine(); // finish line

                sc.nextLine(); // skip

                d += sc.nextInt(); // D-
                sc.nextLine(); // finish line

                sc.nextLine(); // skip

                d += sc.nextInt(); // D
                sc.nextLine(); // finish line

                sc.nextLine(); // skip

                c += sc.nextInt(); // C
                a += sc.nextInt(); // A+
                c += sc.nextInt(); // C-
                sc.nextLine(); // finish line

                sc.nextLine(); // skip

                a += sc.nextInt(); // A-
                b += sc.nextInt(); // B+
                b += sc.nextInt(); // B
                sc.nextLine(); // finish line

                sc.nextLine(); // skip

                a += sc.nextInt(); // A
                b += sc.nextInt(); // B-
                sc.nextLine(); // finish line

                sc.nextLine(); // skip

                c += sc.nextInt(); // C+
                sc.nextLine(); // finish line

                gradeDist.put("a", a);
                gradeDist.put("b", b);
                gradeDist.put("c", c);
                gradeDist.put("d", d);

                JSONObject courseObj;
                JSONObject instructorObj;
                JSONObject gradesObj;

                if (termObj.has(course)) {
                    courseObj = termObj.getJSONObject(course);
                } else {
                    courseObj = new JSONObject();
                }

                if (courseObj.has(instructor)) {
                    instructorObj = courseObj.getJSONObject((instructor));
                } else {
                    instructorObj = new JSONObject();
                }

                if (instructorObj.has(section)) {
                    System.out.println("WARNING: overwriting data for " + course + "-" + section + " (" + instructor + ")");
                }
                gradesObj = gradeDist;

                instructorObj.put(section, gradesObj);

                courseObj.put(instructor, instructorObj);

                termObj.put(course, courseObj);
            }
        }
    }

    private static void mergeJson(JSONObject masterJSON, JSONObject addJSON) {
        for (String course : addJSON.keySet()) {
            if (masterJSON.has(course)) {
                JSONObject masterCourseObj = masterJSON.getJSONObject(course);
                JSONObject addCourseObj = addJSON.getJSONObject(course);
                for (String instructor : addCourseObj.keySet()) {
                    if (masterCourseObj.has(instructor)) {
                        JSONObject masterInstructorObj = masterCourseObj.getJSONObject(instructor);
                        JSONObject addInstructorObj = addCourseObj.getJSONObject(instructor);
                        for (String section : addInstructorObj.keySet()) {
                            if (masterInstructorObj.has(section)) {
                                System.err.println("ERROR: duplicate section found: " + course + "/" + instructor + "/" + section);
                            } else {
                                masterInstructorObj.put(section, addInstructorObj.get(section));
                            }
                            masterCourseObj.put(instructor, masterInstructorObj);
                        }
                    } else {
                        masterCourseObj.put(instructor, addCourseObj.get(instructor));
                    }
                    masterJSON.put(course, masterCourseObj);
                }
            } else {
                masterJSON.put(course, addJSON.get(course));
            }
        }
    }

//    private static JSONObject addGrades(JSONObject term, JSONObject gradeDist) {
//        JSONObject ret = new JSONObject();
//
//        String[] properties = {"a", "b", "c", "d", "f", "totalAF", "i", "s", "u", "q", "x", "total"};
//        for (String property : properties) {
//            ret.put(property, term.getInt(property) + gradeDist.getInt(property));
//        }
//
//        return ret;
//    }
}


