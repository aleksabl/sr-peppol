package eu.peppol.outbound.api;

import eu.peppol.outbound.smp.SmpLookupManager;
import eu.peppol.outbound.soap.SoapDispatcher;
import eu.peppol.outbound.util.Log;
import eu.peppol.start.identifier.*;
import org.w3._2009._02.ws_tra.Create;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.UUID;

/**
 * The Oxalis START outbound module contains all necessary code for sending PEPPOL business documents via the START
 * protocol to a receiving Access Point.
 * <p/>
 * A DocumentSender is the publicly available interface class for sending documents. A particular DocumentSender
 * is dedicated to a particular document and process type. The class is thread-safe.
 * <p/>
 * There are 2 main variants of the sendInvoice method. The first variant uses SMP to find the destination AP. If
 * the SMP lookup fails then the document will not be sent. The second variant sends a document to a specified AP. In
 * this case eu SMP lookup is involved.
 * <p/>
 * User: nigel
 * Date: Oct 17, 2011
 * Time: 4:42:01 PM
 */
@SuppressWarnings({"UnusedDeclaration"})
public class DocumentSender {

    private final DocumentId documentId;
    private final ProcessId processId;
    private final boolean soapLogging;
    private SoapDispatcher soapDispatcher;

    DocumentSender(DocumentId documentId, ProcessId processId, boolean soapLogging) {
        this.documentId = documentId;
        this.processId = processId;
        this.soapLogging = soapLogging;
        this.soapDispatcher = new SoapDispatcher();
    }

    /**
     * sends a PEPPOL business document to a named recipient. The Access Point of the recipient will be identified
     * by SMP lookup.
     *
     * @param xmlDocument the PEPPOL business document to be sent
     * @param sender      the participant id of the document sender
     * @param recipient   the participant id of the document receiver
     */
    public void sendInvoice(InputStream xmlDocument, String sender, String recipient, String  channelId) throws Exception {
        sendInvoice(xmlDocument, sender, recipient, getEndpointAddress(recipient), channelId);
    }

    /**
     * sends a PEPPOL business document to a named recipient. The Access Point of the recipient will be identified
     * by SMP lookup.
     *
     * @param xmlDocument the PEPPOL business document to be sent
     * @param sender      the participant id of the document sender
     * @param recipient   the participant id of the document receiver
     */
    public void sendInvoice(File xmlDocument, String sender, String recipient, String  channelId) throws Exception {
        sendInvoice(xmlDocument, sender, recipient, getEndpointAddress(recipient), channelId);
    }

    /**
     * sends a PEPPOL business document to a named recipient. The destination parameter specifies the address of the
     * recipients Access Point. No SMP lookup will be involved.
     *
     * @param xmlDocument the PEPPOL business document to be sent
     * @param sender      the participant id of the document sender
     * @param recipient   the participant id of the document receiver
     * @param destination the address of the recipient's access point
     */
    public void sendInvoice(InputStream xmlDocument, String sender, String recipient, URL destination, String  channelId) throws Exception {
        log(destination);
        send(getDocumentBuilder().parse(xmlDocument), sender, recipient, destination, new ChannelId(channelId));
    }

    /**
     * sends a PEPPOL business document to a named recipient. The destination parameter specifies the address of the
     * recipients Access Point. No SMP lookup will be involved.
     *
     * @param xmlDocument the PEPPOL business document to be sent
     * @param sender      the participant id of the document sender
     * @param recipient   the participant id of the document receiver
     * @param destination the address of the recipient's access point
     */
    public void sendInvoice(File xmlDocument, String sender, String recipient, URL destination, String channelId) throws Exception {
        log(destination);
        send(getDocumentBuilder().parse(xmlDocument), sender, recipient, destination, new ChannelId(channelId));
    }

    private DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }

    private URL getEndpointAddress(String recipient) {
        return new SmpLookupManager().getEndpointAddress(getParticipantId(recipient), documentId);
    }

    private ParticipantId getParticipantId(String sender) {
        if (!ParticipantId.isValidParticipantIdentifier(sender)) {
            throw new IllegalArgumentException("Invalid participant " + sender);
        }

        return new ParticipantId(sender);
    }

    private void log(URL destination) {
        Log.info("Document destination is " + destination);
    }

    private void send(Document document, String sender, String recipient, URL destination, ChannelId channelId) {
        System.setProperty("com.sun.xml.ws.client.ContentNegotiation", "none");
        System.setProperty("com.sun.xml.wss.debug", "FaultDetail");

        Log.debug("Constructing document body");
        ParticipantId senderId = getParticipantId(sender);
        ParticipantId recipientId = getParticipantId(recipient);
        Create soapBody = new Create();
        soapBody.getAny().add(document.getDocumentElement());

        Log.debug("Constructing SOAP header");
        PeppolMessageHeader messageHeader= new PeppolMessageHeader();
        messageHeader.setChannelId(channelId);
        messageHeader.setMessageId(new MessageId("uuid:" + UUID.randomUUID().toString()));
        messageHeader.setDocumentId(documentId);
        messageHeader.setProcessId(processId);
        messageHeader.setSenderId(senderId);
        messageHeader.setRecipientId(recipientId);

        soapDispatcher.enableSoapLogging(soapLogging);
        soapDispatcher.send(destination, messageHeader, soapBody);
    }
}
