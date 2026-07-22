package com.techindna.springbootjwttemplate.service.mail;

import com.techindna.springbootjwttemplate.entity.email.EmailDetails;
import java.io.File;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailSenderService implements EmailService {

    private final JavaMailSender javaMailSender;
    private final String sender;

    public EmailSenderService(
            JavaMailSender javaMailSender,
            @Value("${spring.mail.username}") String sender) {
        this.javaMailSender = javaMailSender;
        this.sender = sender;
    }

    public void sendMail(EmailDetails details) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sender);
            mailMessage.setTo(details.getRecipient());
            mailMessage.setText(details.getMsgBody());
            mailMessage.setSubject(details.getSubject());
            javaMailSender.send(mailMessage);
        } catch (MailException e) {
            throw new MailSendException("Failed to send email to recipient");
        }
    }

    public void sendMailWithAttachment(EmailDetails details) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setFrom(sender);
            helper.setTo(details.getRecipient());
            helper.setText(details.getMsgBody());
            helper.setSubject(details.getSubject());
            FileSystemResource file = new FileSystemResource(new File(details.getAttachment()));
            helper.addAttachment(file.getFilename(), file);
            javaMailSender.send(mimeMessage);
        } catch (MailException | MessagingException e) {
            throw new MailSendException("Failed to send email to recipient");
        }
    }
}
