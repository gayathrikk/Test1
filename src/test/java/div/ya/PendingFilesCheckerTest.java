package div.ya;

import com.jcraft.jsch.*;
import org.testng.Assert;
import org.testng.annotations.Test;
import javax.mail.*;
import javax.mail.Session;
import javax.mail.internet.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PendingFilesCheckerTest {
    private static final int PORT = 22;
    private static final String USER = "appUser";
    private static final String PASSWORD = "Brain@123";

    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String FROM_EMAIL = "softwaretestingteam9@gmail.com";
    private static final String FROM_PASSWORD = "Health#123";
    private static final String TO_EMAIL = "annotatin.divya@gmail.com";

    private static final Map<String, String> MACHINES = new HashMap<>();

    static {
        MACHINES.put("pp1.humanbrain.in", "/store/nvmestorage/postImageProcessor");
        MACHINES.put("pp2.humanbrain.in", "/mnt/local/nvmestorage/postImageProcessor");
        MACHINES.put("pp3.humanbrain.in", "/mnt/local/nvmestorage/postImageProcessor");
        MACHINES.put("pp4.humanbrain.in", "/mnt/local/nvmestorage/postImageProcessor");
        MACHINES.put("pp5.humanbrain.in", "/mnt/local/nvmestorage/postImageProcessor");
        MACHINES.put("pp7.humanbrain.in", "/mnt/local/nvmestorage/postImageProcessor");
        MACHINES.put("qd4.humanbrain.in", "/mnt/local/nvme2/postImageProcessor");
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("MMM dd").format(new Date());
    }

    private Map<String, List<String>> getFiles(String host, String directory) {
        List<String> pendingFiles = new ArrayList<>();
        List<String> currentFiles = new ArrayList<>();
        Map<String, List<String>> result = new HashMap<>();

        try {
            JSch jsch = new JSch();
            com.jcraft.jsch.Session sshSession = jsch.getSession(USER, host, PORT);
            sshSession.setPassword(PASSWORD);
            sshSession.setConfig("StrictHostKeyChecking", "no");

            System.out.println("🔄 Connecting to " + host + "...");
            sshSession.connect();
            System.out.println("✅ Connected to " + host);

            ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
            channelExec.setCommand("ls -lh --time-style='+%b %d %H:%M' " + directory);
            channelExec.setInputStream(null);
            channelExec.setErrStream(System.err);

            BufferedReader reader = new BufferedReader(new InputStreamReader(channelExec.getInputStream()));
            channelExec.connect();

            String currentDate = getCurrentDate();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.matches(".*[0-9]{2}:[0-9]{2}.*")) {
                    if (line.contains(currentDate)) {
                        currentFiles.add(line);
                    } else {
                        pendingFiles.add(line);
                    }
                }
            }

            channelExec.disconnect();
            sshSession.disconnect();
        } catch (Exception e) {
            System.err.println("❌ Error connecting to " + host + ": " + e.getMessage());
            e.printStackTrace();
        }

        result.put("currentFiles", currentFiles);
        result.put("pendingFiles", pendingFiles);
        return result;
    }

    @Test
    public void testFiles() {
        StringBuilder emailContent = new StringBuilder();
        boolean pendingFilesFound = false;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream newOut = new PrintStream(outputStream);
        System.setOut(newOut);

        for (Map.Entry<String, String> entry : MACHINES.entrySet()) {
            String host = entry.getKey();
            String directory = entry.getValue();

            Map<String, List<String>> files = getFiles(host, directory);
            List<String> currentFiles = files.get("currentFiles");
            List<String> pendingFiles = files.get("pendingFiles");

            System.out.println("\n================================== " + host + " ==================================");

            if (!currentFiles.isEmpty()) {
                System.out.println("📂 Current Files:");
                System.out.println("--------------------------------------------------------------");
                for (String file : currentFiles) {
                    System.out.println(file);
                }
            } else {
                System.out.println("✅ No new files added today on " + host);
            }

            System.out.println();

            if (!pendingFiles.isEmpty()) {
                pendingFilesFound = true;
                System.out.println("⏳ Pending Files:");
                System.out.println("--------------------------------------------------------------");
                for (String file : pendingFiles) {
                    System.out.println(file);
                }
            } else {
                System.out.println("🎉 No pending files remaining on " + host);
                System.out.println("================================== ***DONE*** ==================================");
            }

            Assert.assertNotNull(files, "⚠️ Failed to fetch files from " + host);
        }

        System.out.flush();
        System.setOut(originalOut);

        String consoleOutput = outputStream.toString().trim();
        System.out.println("Captured Output:\n" + consoleOutput);

        if (consoleOutput.isEmpty()) {
            consoleOutput = "✅ No pending files found across all servers.";
        }

        sendEmail("Pending Files Report", consoleOutput);
    }

    private void sendEmail(String subject, String body) {
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", SMTP_HOST);
        properties.put("mail.smtp.port", "587");

        Session session = Session.getInstance(properties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(FROM_EMAIL, FROM_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(FROM_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO_EMAIL));
            message.setSubject(subject);
            String htmlBody = "<pre>" + body.replace("\n", "<br>") + "</pre>";
            message.setContent(htmlBody, "text/html");
            Transport.send(message);
            System.out.println("✅ Email notification sent successfully.");
        } catch (MessagingException e) {
            System.err.println("❌ Error sending email: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
