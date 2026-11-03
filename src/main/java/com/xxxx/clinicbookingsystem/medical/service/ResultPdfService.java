package com.xxxx.clinicbookingsystem.medical.service;

import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.exception.*;
import com.xxxx.clinicbookingsystem.medical.repository.MedicalRecordRepository;
import com.xxxx.clinicbookingsystem.patient.repository.PatientProfileRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;

@Service @RequiredArgsConstructor
public class ResultPdfService {
    private final MedicalRecordRepository records;
    private final PatientProfileRepository profiles;
    private final CurrentAccount current;

    @Transactional(readOnly=true)
    public byte[] create(Long appointmentId, boolean doctor) throws IOException {
        var r=(doctor?records.findForDoctor(appointmentId,current.id()):records.findForPatient(appointmentId,current.id()))
                .orElseThrow(()->new AppException(ErrorCode.MEDICAL_RECORD_NOT_FOUND));
        var a=r.getAppointment();
        if(a.getStatus()!=com.xxxx.clinicbookingsystem.common.enums.AppointmentStatus.COMPLETED)
            throw new AppException(ErrorCode.MEDICAL_RECORD_NOT_FOUND);
        var profile=profiles.findByAccount_Id(a.getPatient().getId()).orElse(null);
        try(var doc=new PDDocument();var fontStream=ResultPdfService.class.getResourceAsStream("/fonts/DejaVuSans.ttf");var out=new ByteArrayOutputStream()) {
            if(fontStream==null)throw new IOException("Missing result PDF font");
            var font=PDType0Font.load(doc,fontStream,true);
            try(var writer=new Writer(doc,font)) {
                writer.line("CLINICCARE",17);
                writer.line("PHIẾU KẾT QUẢ KHÁM",18);
                writer.line("Mã phiếu: KQ-"+r.getId()+"  |  Lịch hẹn: #"+a.getId(),10);
                writer.line("Bệnh nhân: "+(profile!=null&&profile.getFullName()!=null?profile.getFullName():a.getPatient().getUsername()),11);
                writer.line("Mã bệnh nhân: "+a.getPatient().getId()+"  |  Ngày sinh: "+(profile==null||profile.getDateOfBirth()==null?"Chưa cập nhật":profile.getDateOfBirth().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),10);
                writer.line("Điện thoại: "+(a.getPatient().getPhone()==null?"Chưa cập nhật":a.getPatient().getPhone()),10);
                writer.line("Bác sĩ: "+a.getDoctor().getFullName()+"  |  Chuyên khoa: "+a.getDoctor().getSpecialty().getName(),11);
                writer.line("Giờ hẹn: "+a.getSlot().getStartAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))+" (giờ Việt Nam)",10);
                writer.line("Ngày ghi kết quả: "+(r.getCreatedDate()==null?"Chưa xác định":r.getCreatedDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))),10);
                writer.section("CHẨN ĐOÁN",r.getDiagnosis());
                writer.section("GHI NHẬN KHI KHÁM",r.getExaminationNotes());
                writer.section("HƯỚNG DẪN SAU KHÁM",r.getTreatmentAdvice());
                writer.line("Phiếu tra cứu từ ClinicCare; chưa có chữ ký số của bác sĩ.",9);
                writer.line("Đây là phiếu kết quả khám, không phải hóa đơn hoặc phiếu thu.",9);
            }
            for(int i=0;i<doc.getNumberOfPages();i++) {
                try(var c=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)) {
                    c.beginText();c.setFont(font,9);c.newLineAtOffset(46,28);c.showText("KQ-"+r.getId()+"  •  Trang "+(i+1)+" / "+doc.getNumberOfPages());c.endText();
                }
            }
            doc.save(out);return out.toByteArray();
        }
    }

    private static final class Writer implements AutoCloseable {
        final PDDocument doc;final PDFont font;PDPageContentStream stream;float y;
        Writer(PDDocument doc,PDFont font)throws IOException{this.doc=doc;this.font=font;page();}
        void page()throws IOException{if(stream!=null)stream.close();var p=new PDPage(PDRectangle.A4);doc.addPage(p);stream=new PDPageContentStream(doc,p);y=790;}
        void section(String title,String text)throws IOException{if(y<125)page();y-=12;line(title,12);line(text==null||text.isBlank()?"Không ghi nhận thêm.":text,11);y-=8;}
        void line(String raw,float size)throws IOException {
            String text=Normalizer.normalize(raw,Normalizer.Form.NFC).replace('\t',' ');
            StringBuilder safe=new StringBuilder();
            for(int cp:text.codePoints().toArray()) {
                if(cp=='\n'){safe.append('\n');continue;}if(Character.isISOControl(cp))continue;
                String s=new String(Character.toChars(cp));try{font.encode(s);safe.append(s);}catch(IllegalArgumentException e){safe.append('?');}
            }
            for(String paragraph:safe.toString().split("\n",-1)) {
                StringBuilder row=new StringBuilder();
                for(int cp:paragraph.codePoints().toArray()) {
                    String next=new String(Character.toChars(cp));
                    if(font.getStringWidth(row+next)*size/1000>500){int gap=row.lastIndexOf(" ");if(gap>0){String tail=row.substring(gap+1);draw(row.substring(0,gap),size);row.setLength(0);row.append(tail);}else{draw(row.toString(),size);row.setLength(0);}}
                    row.append(next);
                }
                draw(row.toString(),size);
            }
        }
        void draw(String text,float size)throws IOException{if(y<65)page();stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(46,y);stream.showText(text);stream.endText();y-=size+7;}
        public void close()throws IOException{stream.close();}
    }
}
