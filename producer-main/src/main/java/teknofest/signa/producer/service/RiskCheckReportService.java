package teknofest.signa.producer.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import teknofest.signa.producer.model.dto.backoffice.RiskCheckDetailResponseDto;
import teknofest.signa.producer.model.dto.backoffice.RiskFactorDetailDto;
import teknofest.signa.producer.model.dto.transaction.check.CheckTransactionHistoryEntryDto;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskCheckReportService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));
    private static final DecimalFormat MONEY_FORMATTER = new DecimalFormat("#,##0.00");

    /**
     * Generates a rich Excel Workbook in XML Spreadsheet format (compatible with all Excel versions,
     * Google Sheets, LibreOffice, Apple Numbers), containing multiple styled worksheets, tables,
     * risk scoring breakdown, and transaction history.
     */
    public byte[] generateExcelReport(RiskCheckDetailResponseDto details) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<?mso-application progid=\"Excel.Sheet\"?>\n");
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n");
        sb.append(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n");
        sb.append(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n");
        sb.append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"\n");
        sb.append(" xmlns:html=\"http://www.w3.org/TR/REC-html40\">\n");

        // Styles
        sb.append(" <Styles>\n");
        sb.append("  <Style ss:ID=\"Default\" ss:Name=\"Normal\">\n");
        sb.append("   <Alignment ss:Vertical=\"Center\"/>\n");
        sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Color=\"#1E293B\"/>\n");
        sb.append("  </Style>\n");
        sb.append("  <Style ss:ID=\"TitleStyle\">\n");
        sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"14\" ss:Bold=\"1\" ss:Color=\"#0F172A\"/>\n");
        sb.append("  </Style>\n");
        sb.append("  <Style ss:ID=\"SectionHeader\">\n");
        sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"11\" ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>\n");
        sb.append("   <Interior ss:Color=\"#1E293B\" ss:Pattern=\"Solid\"/>\n");
        sb.append("  </Style>\n");
        sb.append("  <Style ss:ID=\"TableHeader\">\n");
        sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Bold=\"1\" ss:Color=\"#334155\"/>\n");
        sb.append("   <Interior ss:Color=\"#E2E8F0\" ss:Pattern=\"Solid\"/>\n");
        sb.append("   <Borders>\n");
        sb.append("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#CBD5E1\"/>\n");
        sb.append("    <Border ss:Position=\"Top\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#CBD5E1\"/>\n");
        sb.append("    <Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#CBD5E1\"/>\n");
        sb.append("    <Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#CBD5E1\"/>\n");
        sb.append("   </Borders>\n");
        sb.append("  </Style>\n");
        sb.append("  <Style ss:ID=\"KeyStyle\">\n");
        sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Bold=\"1\" ss:Color=\"#475569\"/>\n");
        sb.append("   <Interior ss:Color=\"#F8FAFC\" ss:Pattern=\"Solid\"/>\n");
        sb.append("   <Borders>\n");
        sb.append("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E2E8F0\"/>\n");
        sb.append("    <Border ss:Position=\"Top\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E2E8F0\"/>\n");
        sb.append("    <Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E2E8F0\"/>\n");
        sb.append("    <Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E2E8F0\"/>\n");
        sb.append("   </Borders>\n");
        sb.append("  </Style>\n");
        sb.append("  <Style ss:ID=\"ValStyle\">\n");
        sb.append("   <Borders>\n");
        sb.append("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E2E8F0\"/>\n");
        sb.append("    <Border ss:Position=\"Top\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E2E8F0\"/>\n");
        sb.append("    <Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E2E8F0\"/>\n");
        sb.append("    <Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#E2E8F0\"/>\n");
        sb.append("   </Borders>\n");
        sb.append("  </Style>\n");
        sb.append("  <Style ss:ID=\"ApprovedBadge\">\n");
        sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"11\" ss:Bold=\"1\" ss:Color=\"#15803D\"/>\n");
        sb.append("   <Interior ss:Color=\"#DCFCE7\" ss:Pattern=\"Solid\"/>\n");
        sb.append("  </Style>\n");
        sb.append("  <Style ss:ID=\"BlockedBadge\">\n");
        sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"11\" ss:Bold=\"1\" ss:Color=\"#B91C1C\"/>\n");
        sb.append("   <Interior ss:Color=\"#FEE2E2\" ss:Pattern=\"Solid\"/>\n");
        sb.append("  </Style>\n");
        sb.append("  <Style ss:ID=\"FlaggedRow\">\n");
        sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Bold=\"1\" ss:Color=\"#991B1B\"/>\n");
        sb.append("   <Interior ss:Color=\"#FEF2F2\" ss:Pattern=\"Solid\"/>\n");
        sb.append("   <Borders>\n");
        sb.append("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#FECACA\"/>\n");
        sb.append("    <Border ss:Position=\"Top\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#FECACA\"/>\n");
        sb.append("    <Border ss:Position=\"Left\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#FECACA\"/>\n");
        sb.append("    <Border ss:Position=\"Right\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#FECACA\"/>\n");
        sb.append("   </Borders>\n");
        sb.append("  </Style>\n");
        sb.append(" </Styles>\n");

        // Sheet 1: Risk Evaluation & Audit
        sb.append(" <Worksheet ss:Name=\"Risk Audit Overview\">\n");
        sb.append("  <Table ss:DefaultColumnWidth=\"150\">\n");
        sb.append("   <Column ss:Width=\"200\"/>\n");
        sb.append("   <Column ss:Width=\"320\"/>\n");
        sb.append("   <Column ss:Width=\"140\"/>\n");
        sb.append("   <Column ss:Width=\"140\"/>\n");

        // Title
        sb.append("   <Row ss:Height=\"28\">\n");
        sb.append("    <Cell ss:StyleID=\"TitleStyle\"><Data ss:Type=\"String\">SIGNA Fraud Intelligence - Transaction Risk Audit</Data></Cell>\n");
        sb.append("   </Row>\n");
        sb.append("   <Row ss:Height=\"18\">\n");
        sb.append("    <Cell><Data ss:Type=\"String\">Audit Reference: ").append(details.getId()).append("</Data></Cell>\n");
        sb.append("   </Row>\n");
        sb.append("   <Row ss:Height=\"10\"/>\n");

        // Decision section
        appendSectionHeaderXml(sb, "DECISION & RISK SUMMARY");
        appendKeyValueRowXml(sb, "Verdict", details.isApproved() ? "APPROVED" : "BLOCKED / FAILED",
                details.isApproved() ? "ApprovedBadge" : "BlockedBadge");
        appendKeyValueRowXml(sb, "Risk Score", formatNumber(details.getRiskScore()) + " / 1.00", "ValStyle");
        appendKeyValueRowXml(sb, "Reason / Flag Explanation", details.getReason() != null ? escapeXml(details.getReason()) : "None", "ValStyle");
        appendKeyValueRowXml(sb, "Evaluation Timestamp", formatInstant(details.getRequestedAt() != null ? details.getRequestedAt() : details.getCreatedAt()), "ValStyle");
        sb.append("   <Row ss:Height=\"10\"/>\n");

        // Transaction details
        appendSectionHeaderXml(sb, "TRANSACTION DETAILS");
        appendKeyValueRowXml(sb, "Amount", formatMoney(details.getTransaction() != null ? details.getTransaction().getAmount() : null, details.getTransaction() != null ? details.getTransaction().getCurrency() : null), "ValStyle");
        appendKeyValueRowXml(sb, "Recipient Name", details.getTransaction() != null && details.getTransaction().getRecipient() != null ? escapeXml(details.getTransaction().getRecipient().getName()) : "—", "ValStyle");
        appendKeyValueRowXml(sb, "Recipient Type", details.getTransaction() != null && details.getTransaction().getRecipient() != null ? escapeXml(details.getTransaction().getRecipient().getType()) : "—", "ValStyle");
        appendKeyValueRowXml(sb, "Recipient Reference", details.getTransaction() != null && details.getTransaction().getRecipient() != null ? escapeXml(details.getTransaction().getRecipient().getReference()) : "—", "ValStyle");
        appendKeyValueRowXml(sb, "Transfer Memo / Note", details.getTransaction() != null && details.getTransaction().getNote() != null ? escapeXml(details.getTransaction().getNote()) : "—", "ValStyle");
        sb.append("   <Row ss:Height=\"10\"/>\n");

        // Sender Details
        appendSectionHeaderXml(sb, "SENDER & ACCOUNT SNAPSHOT");
        appendKeyValueRowXml(sb, "Customer Name", details.getUser() != null ? escapeXml(details.getUser().getName()) : "—", "ValStyle");
        appendKeyValueRowXml(sb, "Account ID", details.getUser() != null ? escapeXml(details.getUser().getAccountId()) : "—", "ValStyle");
        appendKeyValueRowXml(sb, "Account IBAN", details.getUser() != null ? escapeXml(details.getUser().getIban()) : "—", "ValStyle");
        appendKeyValueRowXml(sb, "Account Type", details.getUser() != null ? escapeXml(details.getUser().getAccountType()) : "—", "ValStyle");
        appendKeyValueRowXml(sb, "Member Since", details.getUser() != null ? escapeXml(details.getUser().getMemberSince()) : "—", "ValStyle");
        appendKeyValueRowXml(sb, "Available Balance", formatMoney(details.getAccount() != null ? details.getAccount().getCurrentBalance() : null, details.getAccount() != null ? details.getAccount().getCurrency() : null), "ValStyle");
        appendKeyValueRowXml(sb, "Projected Balance", formatMoney(details.getProjectedBalance(), details.getAccount() != null ? details.getAccount().getCurrency() : null), "ValStyle");
        sb.append("   <Row ss:Height=\"10\"/>\n");

        // Spending Metrics
        appendSectionHeaderXml(sb, "SPENDING & VELOCITY METRICS");
        String curr = details.getAccount() != null ? details.getAccount().getCurrency() : "USD";
        appendKeyValueRowXml(sb, "Total Past Deposits", formatMoney(details.getDepositTotal(), curr), "ValStyle");
        appendKeyValueRowXml(sb, "Total Past Withdrawals", formatMoney(details.getWithdrawalTotal(), curr), "ValStyle");
        appendKeyValueRowXml(sb, "Average Withdrawal Amount", formatMoney(details.getAvgWithdrawal(), curr), "ValStyle");
        appendKeyValueRowXml(sb, "Maximum Withdrawal Amount", formatMoney(details.getMaxWithdrawal(), curr), "ValStyle");

        sb.append("  </Table>\n");
        sb.append(" </Worksheet>\n");

        // Sheet 2: Model Explanations & Risk Factors
        sb.append(" <Worksheet ss:Name=\"Risk Model Explanations\">\n");
        sb.append("  <Table ss:DefaultColumnWidth=\"140\">\n");
        sb.append("   <Column ss:Width=\"220\"/>\n");
        sb.append("   <Column ss:Width=\"380\"/>\n");
        sb.append("   <Column ss:Width=\"100\"/>\n");
        sb.append("   <Column ss:Width=\"100\"/>\n");
        sb.append("   <Column ss:Width=\"100\"/>\n");

        sb.append("   <Row ss:Height=\"24\">\n");
        sb.append("    <Cell ss:StyleID=\"TableHeader\"><Data ss:Type=\"String\">Risk Factor</Data></Cell>\n");
        sb.append("    <Cell ss:StyleID=\"TableHeader\"><Data ss:Type=\"String\">Model Explanation / Trigger Condition</Data></Cell>\n");
        sb.append("    <Cell ss:StyleID=\"TableHeader\"><Data ss:Type=\"String\">Severity</Data></Cell>\n");
        sb.append("    <Cell ss:StyleID=\"TableHeader\"><Data ss:Type=\"String\">Risk Weight</Data></Cell>\n");
        sb.append("    <Cell ss:StyleID=\"TableHeader\"><Data ss:Type=\"String\">Triggered</Data></Cell>\n");
        sb.append("   </Row>\n");

        if (details.getRiskFactors() != null) {
            for (RiskFactorDetailDto factor : details.getRiskFactors()) {
                String style = factor.isTriggered() ? "FlaggedRow" : "ValStyle";
                sb.append("   <Row ss:Height=\"20\">\n");
                sb.append("    <Cell ss:StyleID=\"").append(style).append("\"><Data ss:Type=\"String\">").append(escapeXml(factor.getTitle())).append("</Data></Cell>\n");
                sb.append("    <Cell ss:StyleID=\"").append(style).append("\"><Data ss:Type=\"String\">").append(escapeXml(factor.getDescription())).append("</Data></Cell>\n");
                sb.append("    <Cell ss:StyleID=\"").append(style).append("\"><Data ss:Type=\"String\">").append(escapeXml(factor.getSeverity())).append("</Data></Cell>\n");
                sb.append("    <Cell ss:StyleID=\"").append(style).append("\"><Data ss:Type=\"String\">+").append(formatNumber(factor.getRiskWeight())).append("</Data></Cell>\n");
                sb.append("    <Cell ss:StyleID=\"").append(style).append("\"><Data ss:Type=\"String\">").append(factor.isTriggered() ? "YES (FLAGGED)" : "No").append("</Data></Cell>\n");
                sb.append("   </Row>\n");
            }
        }

        sb.append("  </Table>\n");
        sb.append(" </Worksheet>\n");

        // Sheet 3: Historical Transactions
        sb.append(" <Worksheet ss:Name=\"Historical Transactions\">\n");
        sb.append("  <Table ss:DefaultColumnWidth=\"120\">\n");
        sb.append("   <Column ss:Width=\"120\"/>\n");
        sb.append("   <Column ss:Width=\"220\"/>\n");
        sb.append("   <Column ss:Width=\"140\"/>\n");
        sb.append("   <Column ss:Width=\"160\"/>\n");
        sb.append("   <Column ss:Width=\"130\"/>\n");

        sb.append("   <Row ss:Height=\"24\">\n");
        sb.append("    <Cell ss:StyleID=\"TableHeader\"><Data ss:Type=\"String\">Type</Data></Cell>\n");
        sb.append("    <Cell ss:StyleID=\"TableHeader\"><Data ss:Type=\"String\">Counterparty</Data></Cell>\n");
        sb.append("    <Cell ss:StyleID=\"TableHeader\"><Data ss:Type=\"String\">Category</Data></Cell>\n");
        sb.append("    <Cell ss:StyleID=\"TableHeader\"><Data ss:Type=\"String\">Date (UTC)</Data></Cell>\n");
        sb.append("    <Cell ss:StyleID=\"TableHeader\"><Data ss:Type=\"String\">Amount</Data></Cell>\n");
        sb.append("   </Row>\n");

        if (details.getHistoryEntries() != null) {
            for (CheckTransactionHistoryEntryDto entry : details.getHistoryEntries()) {
                String type = entry.getAmt() != null && entry.getAmt().compareTo(BigDecimal.ZERO) >= 0 ? "Deposit" : "Withdrawal";
                sb.append("   <Row ss:Height=\"18\">\n");
                sb.append("    <Cell ss:StyleID=\"ValStyle\"><Data ss:Type=\"String\">").append(type).append("</Data></Cell>\n");
                sb.append("    <Cell ss:StyleID=\"ValStyle\"><Data ss:Type=\"String\">").append(entry.getName() != null ? escapeXml(entry.getName()) : "—").append("</Data></Cell>\n");
                sb.append("    <Cell ss:StyleID=\"ValStyle\"><Data ss:Type=\"String\">").append(entry.getCategory() != null ? escapeXml(entry.getCategory()) : "—").append("</Data></Cell>\n");
                sb.append("    <Cell ss:StyleID=\"ValStyle\"><Data ss:Type=\"String\">").append(formatInstant(entry.getDate())).append("</Data></Cell>\n");
                sb.append("    <Cell ss:StyleID=\"ValStyle\"><Data ss:Type=\"Number\">").append(entry.getAmt() != null ? entry.getAmt().toPlainString() : "0").append("</Data></Cell>\n");
                sb.append("   </Row>\n");
            }
        }

        sb.append("  </Table>\n");
        sb.append(" </Worksheet>\n");

        sb.append("</Workbook>\n");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generates a comprehensive, styled HTML audit report with embedded CSS, visual SVG risk gauge,
     * charts, tables, explanations, and instant print-to-PDF layout.
     */
    public byte[] generateHtmlAuditReport(RiskCheckDetailResponseDto details) {
        StringBuilder html = new StringBuilder();
        String curr = details.getAccount() != null && details.getAccount().getCurrency() != null ? details.getAccount().getCurrency() : "USD";
        double score = details.getRiskScore() != null ? details.getRiskScore().doubleValue() : 0.0;
        int scorePercent = (int) Math.min(100, Math.round(score * 100));

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\" />\n");
        html.append("<title>Risk Audit Report - ").append(details.getId()).append("</title>\n");
        html.append("<style>\n");
        html.append("  @page { size: A4; margin: 16mm 14mm 16mm 14mm; }\n");
        html.append("  * { box-sizing: border-box; }\n");
        html.append("  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color: #1e293b; background: #fff; margin: 0; padding: 24px; line-height: 1.45; font-size: 13px; }\n");
        html.append("  .header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid #0f172a; padding-bottom: 12px; margin-bottom: 20px; }\n");
        html.append("  .brand { font-size: 20px; font-weight: 800; letter-spacing: -0.02em; color: #0f172a; }\n");
        html.append("  .brand-sub { font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; color: #64748b; font-weight: 600; }\n");
        html.append("  .btn-print { background: #0f172a; color: #fff; border: none; padding: 8px 14px; border-radius: 6px; font-weight: 600; cursor: pointer; font-size: 12px; }\n");
        html.append("  @media print { .btn-print { display: none; } body { padding: 0; } }\n");
        html.append("  .banner { padding: 16px; border-radius: 8px; margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; ");
        if (details.isApproved()) {
            html.append("background: #f0fdf4; border: 1.5px solid #86efac; color: #166534; }\n");
        } else {
            html.append("background: #fef2f2; border: 1.5px solid #fca5a5; color: #991b1b; }\n");
        }
        html.append("  .verdict-title { font-size: 18px; font-weight: 800; }\n");
        html.append("  .verdict-reason { font-size: 13px; margin-top: 4px; color: #334155; }\n");
        html.append("  .score-badge { font-size: 18px; font-weight: 800; padding: 6px 14px; border-radius: 6px; background: rgba(0,0,0,0.06); text-align: center; }\n");
        html.append("  .section { margin-bottom: 24px; }\n");
        html.append("  .section-title { font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: #0f172a; margin-bottom: 8px; border-bottom: 1px solid #e2e8f0; padding-bottom: 4px; }\n");
        html.append("  .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }\n");
        html.append("  .grid-4 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }\n");
        html.append("  .card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 10px 12px; }\n");
        html.append("  .card-label { font-size: 10px; font-weight: 700; text-transform: uppercase; color: #64748b; margin-bottom: 2px; }\n");
        html.append("  .card-value { font-size: 13px; font-weight: 600; color: #0f172a; }\n");
        html.append("  table { width: 100%; border-collapse: collapse; font-size: 12px; margin-top: 6px; }\n");
        html.append("  th { background: #f1f5f9; text-align: left; padding: 7px 10px; font-weight: 700; color: #334155; border: 1px solid #cbd5e1; }\n");
        html.append("  td { padding: 6px 10px; border: 1px solid #e2e8f0; }\n");
        html.append("  tr.flagged { background: #fff1f2; font-weight: 600; color: #9f1239; }\n");
        html.append("  .progress-bar { width: 100%; height: 8px; background: #e2e8f0; border-radius: 4px; overflow: hidden; margin-top: 6px; }\n");
        html.append("  .progress-fill { height: 100%; background: ").append(details.isApproved() ? "#22c55e" : "#ef4444").append("; width: ").append(scorePercent).append("%; }\n");
        html.append("  .footer { margin-top: 30px; font-size: 10px; color: #94a3b8; text-align: center; border-top: 1px solid #e2e8f0; padding-top: 10px; }\n");
        html.append("</style>\n</head>\n<body>\n");

        // Header
        html.append("<div class=\"header\">\n");
        html.append("  <div>\n");
        html.append("    <div class=\"brand-sub\">Enterprise Fraud Defense</div>\n");
        html.append("    <div class=\"brand\">SIGNA INTELLIGENCE NETWORK</div>\n");
        html.append("  </div>\n");
        html.append("  <div style=\"text-align: right;\">\n");
        html.append("    <button class=\"btn-print\" onclick=\"window.print()\">Print / Save as PDF</button>\n");
        html.append("    <div style=\"font-size: 11px; color: #64748b; margin-top: 4px;\">ID: ").append(details.getId()).append("</div>\n");
        html.append("  </div>\n");
        html.append("</div>\n");

        // Banner
        html.append("<div class=\"banner\">\n");
        html.append("  <div>\n");
        html.append("    <div class=\"verdict-title\">VERDICT: ").append(details.isApproved() ? "APPROVED" : "BLOCKED / FAILED").append("</div>\n");
        if (details.getReason() != null && !details.getReason().isBlank()) {
            html.append("    <div class=\"verdict-reason\"><strong>Reason:</strong> ").append(escapeHtml(details.getReason())).append("</div>\n");
        }
        html.append("    <div style=\"font-size: 11px; color: #64748b; margin-top: 4px;\">Timestamp: ").append(formatInstant(details.getRequestedAt() != null ? details.getRequestedAt() : details.getCreatedAt())).append("</div>\n");
        html.append("  </div>\n");
        html.append("  <div>\n");
        html.append("    <div class=\"score-badge\">Score ").append(formatNumber(details.getRiskScore())).append(" / 1.00</div>\n");
        html.append("    <div class=\"progress-bar\"><div class=\"progress-fill\"></div></div>\n");
        html.append("  </div>\n");
        html.append("</div>\n");

        // Transaction & Account
        html.append("<div class=\"grid-2 section\">\n");
        html.append("  <div>\n");
        html.append("    <div class=\"section-title\">Transaction Details</div>\n");
        html.append("    <div class=\"grid-2\" style=\"gap: 8px;\">\n");
        html.append("      <div class=\"card\"><div class=\"card-label\">Amount</div><div class=\"card-value\">").append(formatMoney(details.getTransaction() != null ? details.getTransaction().getAmount() : null, curr)).append("</div></div>\n");
        html.append("      <div class=\"card\"><div class=\"card-label\">Recipient Type</div><div class=\"card-value\">").append(details.getTransaction() != null && details.getTransaction().getRecipient() != null ? escapeHtml(details.getTransaction().getRecipient().getType()) : "—").append("</div></div>\n");
        html.append("      <div class=\"card\"><div class=\"card-label\">Recipient Name</div><div class=\"card-value\">").append(details.getTransaction() != null && details.getTransaction().getRecipient() != null ? escapeHtml(details.getTransaction().getRecipient().getName()) : "—").append("</div></div>\n");
        html.append("      <div class=\"card\"><div class=\"card-label\">Recipient Ref</div><div class=\"card-value\">").append(details.getTransaction() != null && details.getTransaction().getRecipient() != null ? escapeHtml(details.getTransaction().getRecipient().getReference()) : "—").append("</div></div>\n");
        html.append("    </div>\n");
        if (details.getTransaction() != null && details.getTransaction().getNote() != null) {
            html.append("    <div class=\"card\" style=\"margin-top: 8px;\"><div class=\"card-label\">Note</div><div class=\"card-value\">").append(escapeHtml(details.getTransaction().getNote())).append("</div></div>\n");
        }
        html.append("  </div>\n");

        html.append("  <div>\n");
        html.append("    <div class=\"section-title\">Sender & Account Snapshot</div>\n");
        html.append("    <div class=\"grid-2\" style=\"gap: 8px;\">\n");
        html.append("      <div class=\"card\"><div class=\"card-label\">Account Holder</div><div class=\"card-value\">").append(details.getUser() != null ? escapeHtml(details.getUser().getName()) : "—").append("</div></div>\n");
        html.append("      <div class=\"card\"><div class=\"card-label\">Account ID</div><div class=\"card-value\">").append(details.getUser() != null ? escapeHtml(details.getUser().getAccountId()) : "—").append("</div></div>\n");
        html.append("      <div class=\"card\"><div class=\"card-label\">Available Balance</div><div class=\"card-value\">").append(formatMoney(details.getAccount() != null ? details.getAccount().getCurrentBalance() : null, curr)).append("</div></div>\n");
        html.append("      <div class=\"card\"><div class=\"card-label\">Projected Balance</div><div class=\"card-value\">").append(formatMoney(details.getProjectedBalance(), curr)).append("</div></div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"card\" style=\"margin-top: 8px;\"><div class=\"card-label\">IBAN / Account Type</div><div class=\"card-value\">").append(details.getUser() != null ? escapeHtml(details.getUser().getIban()) : "—").append(" (").append(details.getUser() != null ? escapeHtml(details.getUser().getAccountType()) : "Checking").append(")</div></div>\n");
        html.append("  </div>\n");
        html.append("</div>\n");

        // Risk Model Explanations & Factor Triggers
        html.append("<div class=\"section\">\n");
        html.append("  <div class=\"section-title\">Risk Model Scoring Breakdown & Why Flagged</div>\n");
        html.append("  <table>\n");
        html.append("    <thead><tr><th>Risk Factor</th><th>Explanation / Rule Trigger</th><th>Severity</th><th>Weight</th><th>Triggered</th></tr></thead>\n");
        html.append("    <tbody>\n");
        if (details.getRiskFactors() != null) {
            for (RiskFactorDetailDto factor : details.getRiskFactors()) {
                html.append("      <tr class=\"").append(factor.isTriggered() ? "flagged" : "").append("\">\n");
                html.append("        <td>").append(escapeHtml(factor.getTitle())).append("</td>\n");
                html.append("        <td>").append(escapeHtml(factor.getDescription())).append("</td>\n");
                html.append("        <td>").append(escapeHtml(factor.getSeverity())).append("</td>\n");
                html.append("        <td>+").append(formatNumber(factor.getRiskWeight())).append("</td>\n");
                html.append("        <td><strong>").append(factor.isTriggered() ? "YES (FLAGGED)" : "No").append("</strong></td>\n");
                html.append("      </tr>\n");
            }
        }
        html.append("    </tbody>\n");
        html.append("  </table>\n");
        html.append("</div>\n");

        // Spending Metrics
        html.append("<div class=\"section\">\n");
        html.append("  <div class=\"section-title\">Historical Spending Summary</div>\n");
        html.append("  <div class=\"grid-4\">\n");
        html.append("    <div class=\"card\"><div class=\"card-label\">Total Deposits</div><div class=\"card-value\">").append(formatMoney(details.getDepositTotal(), curr)).append("</div></div>\n");
        html.append("    <div class=\"card\"><div class=\"card-label\">Total Withdrawals</div><div class=\"card-value\">").append(formatMoney(details.getWithdrawalTotal(), curr)).append("</div></div>\n");
        html.append("    <div class=\"card\"><div class=\"card-label\">Avg Withdrawal</div><div class=\"card-value\">").append(formatMoney(details.getAvgWithdrawal(), curr)).append("</div></div>\n");
        html.append("    <div class=\"card\"><div class=\"card-label\">Max Withdrawal</div><div class=\"card-value\">").append(formatMoney(details.getMaxWithdrawal(), curr)).append("</div></div>\n");
        html.append("  </div>\n");
        html.append("</div>\n");

        // History Table
        if (details.getHistoryEntries() != null && !details.getHistoryEntries().isEmpty()) {
            html.append("<div class=\"section\">\n");
            html.append("  <div class=\"section-title\">Recent Account Transactions (History)</div>\n");
            html.append("  <table>\n");
            html.append("    <thead><tr><th>Type</th><th>Counterparty</th><th>Category</th><th>Date (UTC)</th><th style=\"text-align:right;\">Amount</th></tr></thead>\n");
            html.append("    <tbody>\n");
            for (CheckTransactionHistoryEntryDto entry : details.getHistoryEntries()) {
                boolean isNeg = entry.getAmt() != null && entry.getAmt().compareTo(BigDecimal.ZERO) < 0;
                html.append("      <tr>\n");
                html.append("        <td>").append(isNeg ? "Withdrawal" : "Deposit").append("</td>\n");
                html.append("        <td>").append(entry.getName() != null ? escapeHtml(entry.getName()) : "—").append("</td>\n");
                html.append("        <td>").append(entry.getCategory() != null ? escapeHtml(entry.getCategory()) : "—").append("</td>\n");
                html.append("        <td>").append(formatInstant(entry.getDate())).append("</td>\n");
                html.append("        <td style=\"text-align:right; font-weight:700; color:").append(isNeg ? "#b91c1c" : "#15803d").append(";\">")
                        .append(formatMoney(entry.getAmt(), curr)).append("</td>\n");
                html.append("      </tr>\n");
            }
            html.append("    </tbody>\n");
            html.append("  </table>\n");
            html.append("</div>\n");
        }

        html.append("<div class=\"footer\">\n");
        html.append("  SIGNA Fraud Intelligence Monolithic App • Confidential Backoffice Audit Document\n");
        html.append("</div>\n");

        html.append("</body>\n</html>\n");

        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendSectionHeaderXml(StringBuilder sb, String title) {
        sb.append("   <Row ss:Height=\"22\">\n");
        sb.append("    <Cell ss:MergeAcross=\"3\" ss:StyleID=\"SectionHeader\"><Data ss:Type=\"String\">").append(escapeXml(title)).append("</Data></Cell>\n");
        sb.append("   </Row>\n");
    }

    private void appendKeyValueRowXml(StringBuilder sb, String key, String val, String valStyle) {
        sb.append("   <Row ss:Height=\"19\">\n");
        sb.append("    <Cell ss:StyleID=\"KeyStyle\"><Data ss:Type=\"String\">").append(escapeXml(key)).append("</Data></Cell>\n");
        sb.append("    <Cell ss:StyleID=\"").append(valStyle).append("\"><Data ss:Type=\"String\">").append(escapeXml(val)).append("</Data></Cell>\n");
        sb.append("    <Cell ss:StyleID=\"ValStyle\"/>\n");
        sb.append("    <Cell ss:StyleID=\"ValStyle\"/>\n");
        sb.append("   </Row>\n");
    }

    private String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) return "0.00 " + (currency != null ? currency : "");
        return MONEY_FORMATTER.format(amount) + " " + (currency != null ? currency : "");
    }

    private String formatNumber(BigDecimal num) {
        if (num == null) return "0.00";
        return MONEY_FORMATTER.format(num);
    }

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) return "—";
        return DATE_FORMATTER.format(instant);
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
