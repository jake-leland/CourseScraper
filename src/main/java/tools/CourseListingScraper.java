package tools;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CourseListingScraper {
    public static void main(String[] args) throws Exception {
        try (final WebClient webClient = new WebClient()) {
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false); // because Howdy has problems
            webClient.getOptions().setThrowExceptionOnScriptError(false); // because Howdy has problems

            //JSONObject tamuJson = new JSONObject();
            JSONArray subjects = new JSONArray();

            // Howdy Login
            HtmlPage page = webClient.getPage("https://howdy.tamu.edu/");

            // CAS
            page = (HtmlPage) page.executeJavaScript("casRedirect();").getNewPage();
            HtmlForm login = (HtmlForm) page.getElementById("fm1");
            login.getInputByName("username").setValueAttribute(args[0]);
            login.getInputByName("password").setValueAttribute(args[1]);

            // Howdy Home
            page = login.getButtonsByName("").get(0).click();

            // My Record
            page = page.getAnchorByText("My Record").click();

            // Search Class Schedule
            page = page.getAnchorByText("Search Class Schedule").click();

            // (isolate frame)
            page = webClient.getPage(page.getFrameByName("content").getEnclosedPage().getUrl());

            // Select Term
            page = page.getForms().get(1).getInputByValue("Submit").click();
            HtmlForm subjectsForm = page.getForms().get(1);
            HtmlSelect subjectsSelect = subjectsForm.getSelectByName("sel_subj");

            for (HtmlOption subjectOption : subjectsSelect.getOptions()) {
                subjectsSelect.setSelectedIndex(subjectOption.getIndex() - 1);

                JSONObject subjectJson = new JSONObject();

                JSONObject coursesJson = new JSONObject();

                // Specific Subject
                final HtmlPage subjectPage = subjectsForm.getInputByValue("Course Search ").click();
                System.out.println(subjectOption.getText());
                HtmlTable subjectTable = (HtmlTable) subjectPage.getBody().getElementsByAttribute("table", "class", "datadisplaytable").get(0);

                String subject = subjectOption.getValueAttribute();

                boolean skip = true;
                for (HtmlTableRow courseRow : subjectTable.getRows()) {
                    if (skip) {
                        skip = false;
                        continue;
                    } // skip first row

                    JSONObject courseJson = new JSONObject();

                    HtmlForm courseForm = (HtmlForm) courseRow.getCells().get(3).getHtmlElementsByTagName("form").get(0);

                    JSONObject sectionsJson = new JSONObject();

                    // Specific section
                    final HtmlPage sectionPage = courseForm.getInputByValue("View Sections").click();
                    HtmlTable sectionTable = (HtmlTable) sectionPage.getBody().getElementsByAttribute("table", "class", "datadisplaytable").get(0);

                    boolean skip1 = true;
                    boolean skip2 = true;

                    String section = "";
                    JSONObject sectionJson = new JSONObject();
                    int classes = 0;
                    for (HtmlTableRow sectionRow : sectionTable.getRows()) {
                        if (skip1) {
                            skip1 = false;
                            continue;
                        } // skip first row
                        if (skip2) {
                            skip2 = false;
                            continue;
                        } // skip second row

                        List<HtmlTableCell> sectionCells = sectionRow.getCells();

                        if (!sectionCells.get(1).getTextContent().equals(" ")) {
                            //String subject = sectionCells.get(2).getTextContent();
                            //String course = sectionCells.get(3).getTextContent();

                            section = sectionCells.get(4).getTextContent();
                            sectionJson = new JSONObject();

                            sectionJson.put("select", (sectionCells.get(0).getTextContent().equals("C") ? "C" : "SR"));
                            sectionJson.put("crn", sectionCells.get(1).getTextContent());
                            sectionJson.put("subject", sectionCells.get(2).getTextContent());
                            sectionJson.put("course", sectionCells.get(3).getTextContent());
                            sectionJson.put("section", section);
                            sectionJson.put("campus", sectionCells.get(5).getTextContent());
                            sectionJson.put("credits", sectionCells.get(6).getTextContent());
                            sectionJson.put("title", sectionCells.get(7).getTextContent().substring(0, sectionCells.get(7).getTextContent().indexOf('\n')));
                            sectionJson.put("capacity", sectionCells.get(10).getTextContent());
                            sectionJson.put("active", sectionCells.get(11).getTextContent());
                            sectionJson.put("remaining", sectionCells.get(12).getTextContent());

                            classes = 1;
                        }
                        sectionJson.append("days", sectionCells.get(8).getTextContent());
                        sectionJson.append("time", sectionCells.get(9).getTextContent());
                        sectionJson.append("instructor", sectionCells.get(13).getTextContent().trim());
                        sectionJson.append("date (mm/dd)", sectionCells.get(14).getTextContent());
                        sectionJson.append("location", sectionCells.get(15).getTextContent());

                        sectionJson.put("classes", classes++);

                        sectionsJson.put(section, sectionJson);
                    }
                    courseJson.put("sections", sectionsJson);
                    courseJson.put("title", courseRow.getCells().get(2).getTextContent());

                    coursesJson.put(courseRow.getCells().get(1).getTextContent(), courseJson);
                }

                subjectJson.put("courses", coursesJson);
                subjectJson.put("title", subjectOption.getText());
                subjectJson.put("updated", new SimpleDateFormat("EEE, d MMM yyyy 'at' hh:mm aa").format(new Date()));

                try (FileWriter file = new FileWriter("data/subjects/" + subject + ".json")) {
                    file.write(subjectJson.toString());
                }
                subjects.put(subject);
            }
            try (FileWriter file = new FileWriter("data/subjects.json")) {
                file.write(subjects.toString());
            }
        }
    }
}
