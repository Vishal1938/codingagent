Let me address both — the OCR fix first, then strategic feature ideas.

---

## Part 1 — Fix for image-based PDFs (OCR)

PDFBox can't read text from PDFs that are just scanned images. You need to detect this case and fall back to OCR. **Tesseract** is the standard free Java library for this.

**Add dependency:**

```xml
<dependency>
    <groupId>net.sourceforge.tess4j</groupId>
    <artifactId>tess4j</artifactId>
    <version>5.11.0</version>
</dependency>
```

**Update `extractPdfPages` in `PageQuestionExtractorService.java`:**

```java
private List<String> extractPdfPages(MultipartFile file) throws IOException {
    List<String> pages = new ArrayList<>();
    try (PDDocument document = PDDocument.load(file.getInputStream())) {
        PDFTextStripper stripper = new PDFTextStripper();
        int numPages = document.getNumberOfPages();

        for (int i = 1; i <= numPages; i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            String text = stripper.getText(document).trim();

            // If page has substantial text, use it as-is
            if (text.length() > 50) {
                log.info("📄  Page {} — text layer found ({} chars)", i, text.length());
                pages.add(text);
            } else {
                // Image-based page — run OCR
                log.info("🔍  Page {} — no text layer, running OCR...", i);
                String ocrText = ocrPage(document, i);
                log.info("🔍  Page {} — OCR extracted {} chars", i, ocrText.length());
                pages.add(ocrText);
            }
        }
    }
    return pages;
}

private String ocrPage(PDDocument document, int pageNumber) {
    try {
        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, 300);

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata");  // Linux
        // Windows local: "C:/Program Files/Tesseract-OCR/tessdata"
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(1);
        tesseract.setOcrEngineMode(1);

        return tesseract.doOCR(image).trim();
    } catch (Exception e) {
        log.error("❌  OCR failed for page {}: {}", pageNumber, e.getMessage());
        return "";
    }
}
```

**Imports to add:**

```java
import org.apache.pdfbox.rendering.PDFRenderer;
import net.sourceforge.tess4j.Tesseract;
import java.awt.image.BufferedImage;
```

**For Railway deployment** — Tesseract needs to be installed in the container. Add a `nixpacks.toml` file at project root:

```toml
[phases.setup]
aptPkgs = ["tesseract-ocr", "tesseract-ocr-eng"]
```

Railway auto-detects this and installs Tesseract during build.

---

## Part 2 — Feature ideas to make it a one-stop student platform

Here's a roadmap from quick wins to bigger features. I've grouped them by effort:

### Quick wins (1-2 days each)

**Multiple language support** — Hindi, regional languages. Extend the OCR with `tesseract.setLanguage("eng+hin")` and let users pick the answer language in the upload form. Huge for Indian students.

**Difficulty level toggle** — Let the user choose "Brief", "Detailed", or "Exam-ready" answers in the upload UI. Just append it to the system prompt. Same backend, much more flexible output.

**Subject presets** — Dropdown with "Math", "Physics", "Biology", "Computer Science" etc. Each preset has a tuned system prompt. Reduces friction — student doesn't need to write a system prompt.

**Recent uploads dashboard tile** — Already have history, add a stats card on top: "12 reports generated · 287 questions answered · 4.2 hrs saved". Encourages return visits.

### Mid-effort (3-7 days each)

**Question bank / flashcards mode** — After extracting Q&A, also generate flashcards. Show one question at a time, user reveals answer, marks "got it" or "review again". Spaced repetition for exam prep.

**Quiz mode** — Take an uploaded paper and turn it into an interactive quiz. Student types/selects answers, gets graded instantly, sees explanations. This is the killer feature — converts passive PDFs into active learning.

**Doubt solver chat** — Upload notes/textbook, then chat with it. "Explain photosynthesis from this chapter", "Give me 5 MCQs on Newton's laws". RAG over their own documents. Massive value.

**Topic extraction & summary** — Beyond questions, extract key topics/concepts from notes PDFs. Generate one-page summaries. Mind maps using Mermaid.

**Study schedule generator** — Upload syllabus → AI breaks it into a day-by-day study plan based on exam date. Trello-style cards.

**Solution video search** — For complex problems where text isn't enough, search YouTube for related explanations and embed the most-viewed/highest-rated videos next to the question.

### Bigger features (1-2 weeks each)

**Past papers archive** — Crowdsourced or scraped database of question papers organized by board/subject/year. Students can browse without uploading, search for "CBSE Class 10 Maths 2024" and get instant Q&A.

**Handwritten answer evaluation** — Student writes answers on paper, photographs them, uploads. AI compares against the correct answer and gives feedback + a score. Mock-exam grading.

**Study groups / peer learning** — Group of students share their question banks and notes. Collaborative annotation.

**Mock test generator** — From their previous papers, generate a new test of similar difficulty/pattern. Time-bound. Sends results to email.

**Performance analytics** — Track which topics the student gets wrong repeatedly. "You score 60% on Trigonometry — practice these 10 questions". Build a personalized weak-area dashboard.

**Voice mode** — Read questions aloud, accept spoken answers (Web Speech API). Great for revising on the move or visually impaired students.

### Monetization-friendly additions

**Tutor marketplace** — If AI answer isn't enough, request a human tutor. Take a commission.

**Premium tier** — Free: 5 PDFs/month, basic answers. Paid: unlimited, longer documents, faster processing, downloadable mock tests, priority support.

**Coaching institute / school accounts** — White-label the platform for coaching centers. Teachers upload, students access via institute code. B2B revenue.

---

**My honest recommendation for what to build next:**

Pick **Quiz mode** + **Doubt solver chat**. Reasons:

1. **Quiz mode** turns your one-time use case (upload → get PDF) into a repeat-use case (upload → study every day). The retention impact is massive.

2. **Doubt solver chat** is the most expected feature in 2026 — every student already chats with ChatGPT for doubts. By letting them upload their own notes/textbook and chat with it, you become the personalized version they actually need. Use Spring AI's existing `ChatClient` with conversation memory.

3. Together they cover the full study flow: revise (quiz) → understand (chat) → practice (your existing question-paper feature).

Want me to build out **Quiz mode** or **Doubt solver chat** next?