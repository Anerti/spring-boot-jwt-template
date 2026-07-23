package com.techindna.springbootjwttemplate.service.mail;

import com.techindna.springbootjwttemplate.entity.email.EmailDetails;
import java.io.File;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailSenderService implements EmailService {

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;
    private final String sender;

    public EmailSenderService(
            JavaMailSender javaMailSender,
            TemplateEngine templateEngine,
            @Value("${spring.mail.username}") String sender) {
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.sender = sender;
    }

    @Async("mailExecutor")
    public void sendMail(EmailDetails details) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(sender);
            helper.setTo(details.getRecipient());
            helper.setSubject(details.getSubject());

            Context context = new Context();
            context.setVariables(details.getVariables());
            String html = templateEngine.process(details.getTemplate(), context);
            helper.setText(html, true);

            javaMailSender.send(mimeMessage);
        } catch (MessagingException | MailException e) {
            throw new MailSendException("Failed to send email to recipient");
        }
    }

    @Async("mailExecutor")
    public void sendMailWithAttachment(EmailDetails details) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(sender);
            helper.setTo(details.getRecipient());
            helper.setSubject(details.getSubject());

            Context context = new Context();
            context.setVariables(details.getVariables());
            String html = templateEngine.process(details.getTemplate(), context);
            helper.setText(html, true);

            FileSystemResource file = new FileSystemResource(new File(details.getAttachment()));
            helper.addAttachment(file.getFilename(), file);
            javaMailSender.send(mimeMessage);
        } catch (MailException | MessagingException e) {
            throw new MailSendException("Failed to send email to recipient");
        }
    }
}
