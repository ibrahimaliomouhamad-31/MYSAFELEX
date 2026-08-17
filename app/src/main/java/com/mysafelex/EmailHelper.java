package com.mysafelex;

import android.os.AsyncTask;
import android.util.Log;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailHelper extends AsyncTask<Void, Void, Void> {

    private final String emailTo;
    private final String subject;
    private final String body;

    public EmailHelper(String emailTo, String subject, String body) {
        this.emailTo = emailTo;
        this.subject = subject;
        this.body = body;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        final String username = "TON_EMAIL@gmail.com";
        final String password = "TON_MOT_DE_PASSE_16_LETTRES";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        
        // FAILLE 2 : Timeouts pour éviter le blocage réseau
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailTo));
            message.setSubject(subject);
            message.setContent(body, "text/html; charset=utf-8");
            Transport.send(message);
            Log.d("EmailHelper", "Email envoyé avec succès !");
        } catch (MessagingException e) {
            Log.e("EmailHelper", "Erreur envoi email: " + e.getMessage());
        }
        return null;
    }
}
