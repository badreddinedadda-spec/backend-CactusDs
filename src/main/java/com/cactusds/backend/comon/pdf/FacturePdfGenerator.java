package com.cactusds.backend.comon.pdf;

import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Facture;
import org.openpdf.text.*;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class FacturePdfGenerator {

    @Value("${app.company.name}")
    private String companyName;
    @Value("${app.company.address}")
    private String companyAddress;
    @Value("${app.company.ice}")
    private String companyIce;
    @Value("${app.company.rc}")
    private String companyRc;
    @Value("${app.company.email}")
    private String companyEmail;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color ACCENT = new Color(45, 45, 45);

    public byte[] generate(Facture facture, List<Commande> commandes) {
        Document document = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            document.add(new Paragraph(companyName, titleFont));
            document.add(new Paragraph(companyAddress, normalFont));
            if (companyIce != null && !companyIce.isBlank()) {
                String rcPart = (companyRc != null && !companyRc.isBlank()) ? "  -  RC: " + companyRc : "";
                document.add(new Paragraph("ICE: " + companyIce + rcPart, smallFont));
            }
            document.add(new Paragraph(companyEmail, smallFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("FACTURE N° " + facture.getNumero(), titleFont));
            document.add(Chunk.NEWLINE);

            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);

            PdfPCell clientCell = new PdfPCell();
            clientCell.setBorder(Rectangle.NO_BORDER);
            String clientNom = facture.getUser().getFullName() != null
                    ? facture.getUser().getFullName() : facture.getUser().getEmail();
            clientCell.addElement(new Paragraph("Facturé à:", boldFont));
            clientCell.addElement(new Paragraph(clientNom, normalFont));
            clientCell.addElement(new Paragraph(facture.getUser().getEmail(), normalFont));
            infoTable.addCell(clientCell);

            PdfPCell metaCell = new PdfPCell();
            metaCell.setBorder(Rectangle.NO_BORDER);
            metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(new Paragraph("Date d'émission: " + facture.getDateEmission().format(DATE_FMT), normalFont));
            metaCell.addElement(new Paragraph("Période: " + facture.getPeriodeDebut().format(DATE_FMT)
                    + " – " + facture.getPeriodeFin().format(DATE_FMT), normalFont));
            metaCell.addElement(new Paragraph("Statut: " + facture.getStatut().name(), normalFont));
            infoTable.addCell(metaCell);

            document.add(infoTable);
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(new float[]{4f, 1.5f, 1.5f, 2f});
            table.setWidthPercentage(100);
            for (String header : List.of("Désignation", "Durée", "Statut", "Montant")) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(ACCENT);
                cell.setPadding(6f);
                table.addCell(cell);
            }
            for (Commande c : commandes) {
                table.addCell(new Phrase(c.getOffre().getNom(), normalFont));
                table.addCell(new Phrase(c.getDuree() == Commande.Duree.ANNUEL ? "Annuel" : "Mensuel", normalFont));
                table.addCell(new Phrase(c.getStatut().name(), normalFont));
                PdfPCell montantCell = new PdfPCell(new Phrase(formatMontant(c.getPrixTotal()), normalFont));
                montantCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(montantCell);
            }
            document.add(table);
            document.add(Chunk.NEWLINE);

            Paragraph total = new Paragraph("Total: " + formatMontant(facture.getMontantTotal()) + " MAD", boldFont);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);

            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Merci de votre confiance.", smallFont));

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate invoice PDF", e);
        }
        return out.toByteArray();
    }

    private String formatMontant(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.FRANCE);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount.setScale(2, RoundingMode.HALF_UP));
    }
}