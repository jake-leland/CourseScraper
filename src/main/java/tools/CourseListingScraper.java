package tools;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public class CourseListingScraper {
    private static final boolean TEST = false;

    public static void main(String[] args) throws Exception {
        String domain = TEST ? "http://localhost:3000" : "https://aggie-scheduler.mybluemix.net";

        Scanner kb = new Scanner(System.in);

        try (final WebClient webClient = new WebClient()) {
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false); // because Howdy has problems
            webClient.getOptions().setThrowExceptionOnScriptError(false); // because Howdy has problems

            JSONArray courses = new JSONArray();
            JSONArray subjects = new JSONArray();

            // CAS
            System.out.println("CAS");
            HtmlPage page = webClient.getPage("https://cas.tamu.edu/cas/login?service=https://compass-sso.tamu.edu:443/ssomanager/c/SSB?pkg=bwykfcls.p_sel_crse_search;renew=true");
            HtmlForm login = (HtmlForm) page.getElementById("fm1");
            login.getInputByName("username").setValueAttribute(args[0]);
            login.getInputByName("password").setValueAttribute(args[1]);

            // Search Class Schedule
            System.out.println("Search Class Schedule");
            page = login.getButtonsByName("").get(0).click();

            // Select Term
            HtmlForm termForm = page.getForms().get(1);
            HtmlSelect termSelect = termForm.getSelectByName("p_term");
            System.out.println("\nSELECT TERM:");
            for (int i = 0; i < termSelect.getOptionSize(); i++) {
                HtmlOption t = termSelect.getOption(i);
                System.out.println("[" + i + "]" + t.getText() + " " + t.getValueAttribute());
            }
            int choice = kb.nextInt();
            HtmlOption currentTerm = termSelect.getOption(choice);
            termSelect.setSelectedIndex(choice);
            String term = currentTerm.getValueAttribute();
            String termTitle = currentTerm.getText();

            WebRequest requestSettings1 = new WebRequest(
                    new URL(domain + "/api/term"), HttpMethod.POST);
            requestSettings1.setRequestParameters(new ArrayList());
            requestSettings1.getRequestParameters().add(new NameValuePair("term", term));
            requestSettings1.getRequestParameters().add(new NameValuePair("name", termTitle));
            Page page1 = webClient.getPage(requestSettings1);

            page = termForm.getInputByValue("Submit").click();

            // Accept Terms of Use
            if (page.getTitleText().equals("Student Terms of Use")) {
                HtmlForm stouForm = page.getForms().get(1);
                page = stouForm.getInputByValue("I AGREE").click();
            }

            // Verify Location
            if (page.getTitleText().equals("Student Location Selection")) {
                HtmlForm locationForm1 = page.getForms().get(1);
                HtmlForm locationForm2 = page.getForms().get(2);
                HtmlSelect stateSelect = locationForm1.getSelectByName("stat");
                String selectedState = stateSelect.getSelectedOptions().get(0).getValueAttribute();
                System.out.println("State selected: " + selectedState);

                HtmlSelect countrySelect = locationForm2.getSelectByName("country");
                String selectedCountry = countrySelect.getSelectedOptions().get(0).getValueAttribute();
                System.out.println("Country selected: " + selectedCountry);

                if (selectedState.equals("TX") && selectedCountry.equals("US")) {
                    page = locationForm2.getInputByValue("Submit and Proceed to Class Schedule").click();
                } else {
                    System.err.println("Location not properly set");
                    return;
                }
            }

            // Select Subject
            HtmlForm subjectsForm = page.getForms().get(1);
            HtmlSelect subjectsSelect = subjectsForm.getSelectByName("sel_subj");

            for (int i = 0; i < subjectsSelect.getOptionSize(); i++) {
                HtmlOption subjectOption = subjectsSelect.getOption(i);
                subjectsSelect.setSelectedIndex(i);

                JSONArray specificCourses = new JSONArray();

                // Specific Subject
                final HtmlPage subjectPage = subjectsForm.getInputByValue("Course Search").click();

                JSONObject subjectJson = new JSONObject();

                String subject = subjectOption.getValueAttribute();
                String subjectTitle = subjectOption.getText();

                subjectJson.put("_id", subject);
                subjectJson.put("subject", subject);
                subjectJson.put("title", subjectTitle);

                subjects.put(subjectJson);

                System.out.println(subjectTitle);
                HtmlTable subjectTable = (HtmlTable) subjectPage.getBody().getElementsByAttribute("table", "class", "datadisplaytable").get(0);

                boolean skip = true;
                for (HtmlTableRow courseRow : subjectTable.getRows()) {
                    if (skip) {
                        skip = false;
                        continue;
                    } // skip first row

                    JSONObject courseJson = new JSONObject();

                    String course = courseRow.getCells().get(1).getTextContent();
                    String courseTitle = courseRow.getCells().get(2).getTextContent();

                    courseJson.put("_id", subject + " " + course);
                    courseJson.put("subject", subject);
                    courseJson.put("course", course);
                    courseJson.put("title", courseTitle);

                    specificCourses.put(courseJson);
                    courses.put(courseJson);
                }

                try (FileWriter file = new FileWriter("data/listings/courses/" + subjectOption.getValueAttribute() + ".json")) {
                    file.write(specificCourses.toString());
                }
                WebRequest requestSettings2 = new WebRequest(
                        new URL(domain + "/api/courses"), HttpMethod.POST);
                requestSettings2.setRequestParameters(new ArrayList());
                requestSettings2.getRequestParameters().add(new NameValuePair("term", term));
                requestSettings2.getRequestParameters().add(new NameValuePair("courses", specificCourses.toString()));
                Page page2 = webClient.getPage(requestSettings2);
            }
            try (FileWriter file = new FileWriter("data/listings/courses.json")) {
                file.write(courses.toString());
            }

            try (FileWriter file = new FileWriter("data/listings/subjects.json")) {
                file.write(subjects.toString());
            }
            WebRequest requestSettings3 = new WebRequest(
                    new URL(domain + "/api/subjects"), HttpMethod.POST);
            requestSettings3.setRequestParameters(new ArrayList());
            requestSettings3.getRequestParameters().add(new NameValuePair("term", term));
            requestSettings3.getRequestParameters().add(new NameValuePair("subjects", subjects.toString()));
            Page page3 = webClient.getPage(requestSettings3);
        }
    }
}
