/* Yougi is a web application conceived to manage user groups or
* communities focused on a certain domain of knowledge, whose members are
* constantly sharing information and participating in social and educational
* events. Copyright (C) 2011 Hildeberto Mendonça.
*
* This application is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as published by the
* Free Software Foundation; either version 2.1 of the License, or (at your
* option) any later version.
*
* This application is distributed in the hope that it will be useful, but
* WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
* or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
* License for more details.
*
* There is a full copy of the GNU Lesser General Public License along with
* this library. Look for the file license.txt at the root level. If you do not
* find it, write to the Free Software Foundation, Inc., 59 Temple Place,
* Suite 330, Boston, MA 02111-1307 USA.
* */
package org.yougi.event.web.controller;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfWriter;
import org.yougi.business.ApplicationPropertyBean;
import org.yougi.entity.ApplicationProperty;
import org.yougi.entity.Properties;
import org.yougi.entity.UserAccount;
import org.yougi.event.business.AttendeeBean;
import org.yougi.event.business.EventBean;
import org.yougi.event.business.EventVenueBean;
import org.yougi.event.entity.Attendee;
import org.yougi.event.entity.Event;
import org.yougi.event.entity.Venue;
import org.yougi.util.annotation.ManagedProperty;
import org.yougi.web.controller.UserProfileMBean;
import org.yougi.web.report.EventAttendeeCertificate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Hildeberto Mendonca - http://www.hildeberto.com
 */
@Named
@RequestScoped
public class AttendeeMBean implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(AttendeeMBean.class.getSimpleName());

    private static final long serialVersionUID = 1L;

    @EJB
    private AttendeeBean attendeeBean;

    @EJB
    private EventBean eventBean;

    @EJB
    private EventVenueBean eventVenueBean;

    @EJB
    private ApplicationPropertyBean applicationPropertyBean;

    @Inject
    @ManagedProperty("#{param.id}")
    private String id;

    @Inject
    @ManagedProperty("#{param.eventId}")
    private String eventId;

    @Inject
    private UserProfileMBean userProfileMBean;

    @Resource
    private HttpServletResponse response;

    @Inject
    private FacesContext context;

    private Attendee attendee;

    private List<Event> attendedEvents;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public void setUserProfileMBean(UserProfileMBean userProfileMBean) {
        this.userProfileMBean = userProfileMBean;
    }

    public Attendee getAttendee() {
        return this.attendee;
    }

    public void setAttendee(Attendee attendee) {
        this.attendee = attendee;
    }

    public Boolean getIsAttending() {
        return this.attendee != null && this.attendee.getId() != null;
    }

    public Boolean getAttended() {
        return this.attendee != null && this.attendee.getAttended() != null && this.attendee.getAttended();
    }

    public List<Event> getAttendedEvents() {
        if (this.attendedEvents == null && this.attendee != null) {
            this.attendedEvents = attendeeBean.findAttendeedEvents(this.attendee.getUserAccount());
        }
        return this.attendedEvents;
    }

    public String attendEvent() {
        this.attendee.setRegistrationDate(Calendar.getInstance().getTime());
        attendeeBean.save(this.attendee);
        return "attendee?id=" + this.attendee.getId();
    }

    public String cancelAttendance() {
        attendeeBean.remove(this.attendee.getId());
        this.attendee.setId(null);
        return "attendee";
    }

    public String confirmAttendance() {
        this.attendee.setAttended(Boolean.TRUE);
        attendeeBean.save(this.attendee);
        return "event?faces-redirect=true&id=" + this.attendee.getEvent().getId() + "&tab=4";
    }

    public void getCertificate() {
        if (this.attendee.getAttended() != null && !this.attendee.getAttended()) {
            return;
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-disposition", "inline=filename=file.pdf");

        try {
            Document document = new Document(PageSize.A4.rotate());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, output);
            document.open();

            ApplicationProperty fileRepositoryPath = applicationPropertyBean.findApplicationProperty(Properties.FILE_REPOSITORY_PATH);

            EventAttendeeCertificate eventAttendeeCertificate = new EventAttendeeCertificate(document);
            if (this.attendee.getEvent().getCertificateTemplate() != null && !this.attendee.getEvent().getCertificateTemplate().isEmpty()) {
                StringBuilder certificateTemplatePath = new StringBuilder();
                certificateTemplatePath.append(fileRepositoryPath.getPropertyValue());
                certificateTemplatePath.append("/");
                certificateTemplatePath.append(this.attendee.getEvent().getCertificateTemplate());
                eventAttendeeCertificate.setCertificateTemplate(writer, certificateTemplatePath.toString());
            }

            List<Venue> venues = eventVenueBean.findEventVenues(this.attendee.getEvent());

            this.attendee.getEvent().setVenues(venues);
            this.attendee.generateCertificateData();
            this.attendeeBean.save(this.attendee);
            eventAttendeeCertificate.generateCertificate(this.attendee);

            document.close();

            response.getOutputStream().write(output.toByteArray());
            response.getOutputStream().flush();
            response.getOutputStream().close();
            context.responseComplete();
        } catch (IOException | DocumentException ioe) {
            LOGGER.log(Level.SEVERE, ioe.getMessage(), ioe);
        }
    }

    @PostConstruct
    public void load() {
        if (this.id != null && !this.id.isEmpty()) {
            this.attendee = attendeeBean.find(id);
        } else if (eventId != null && !eventId.isEmpty()) {
            Event event = eventBean.find(eventId);
            UserAccount userAccount = userProfileMBean.getUserAccount();
            this.attendee = attendeeBean.find(event, userAccount);

            if (this.attendee == null) {
                this.attendee = new Attendee();
                this.attendee.setEvent(event);
                this.attendee.setUserAccount(userAccount);
            }
        }
    }
}