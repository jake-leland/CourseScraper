package tools;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONArray;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;

public class GradeHistoryScraper {
    public static void main(String[] args) throws Exception {
        try (final WebClient webClient = new WebClient()) {
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            webClient.getOptions().setThrowExceptionOnScriptError(false);

            HtmlPage page = webClient.getPage("http://web-as.tamu.edu/gradereport/");

            HtmlSelect yearSelect = (HtmlSelect) page.getElementById("ctl00_plcMain_lstGradYear");
            HtmlSelect collegeSelect = (HtmlSelect) page.getElementById("ctl00_plcMain_lstGradCollege");

            JSONArray subjects = new JSONArray();

            for (HtmlOption yearOption : yearSelect.getOptions()) {
                int year = Integer.parseInt(yearOption.getValueAttribute());
                if (year >= 2009) {
                    for (int semester = 1; semester <= 3; semester++) {
                        for (HtmlOption collegeOption : collegeSelect.getOptions()) {
                            String college = collegeOption.getValueAttribute();
                            String pdfFile = "http://web-as.tamu.edu/gradereport/PDFReports/" + year + semester + "/grd" + year + semester + college + ".pdf";
                            try {
                                String txtFile = downloadTxt(pdfFile);
                                System.out.println(pdfFile + " > " + txtFile);
                                ArrayList<String> departments = makeJson(txtFile);
                                departments.forEach(subjects::put);
                            } catch (Exception e) {
                                System.out.println(pdfFile + " > not found");
//                            e.printStackTrace();
                            }
                        }
                    }
                }
            }
            try (FileWriter file = new FileWriter("data/subjects.json")) {
                file.write(subjects.toString());
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
     * Given a filepath to a .txt file, extracts the information and saves it in .json format
     * Returns an ArrayList of all departments that appeared in the file
     */
    private static ArrayList<String> makeJson(String txtFile) {
        ArrayList<String> departments = new ArrayList<>();
        return departments;
    }
}


