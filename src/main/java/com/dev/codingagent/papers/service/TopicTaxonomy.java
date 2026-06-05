package com.dev.codingagent.papers.service;


import java.util.List;
import java.util.Map;

/**
 * Hardcoded topic taxonomy per subject.
 * The LLM must pick a topic from these lists — prevents
 * "Trigonometry" vs "Trig" vs "trig" duplicates that would
 * break filtering and analytics.
 *
 * Expand these lists as the platform grows.
 */
public final class TopicTaxonomy {

    private TopicTaxonomy() {}

    public static final Map<String, List<String>> TOPICS = Map.of(
            "Mathematics", List.of(
                    "Algebra", "Geometry", "Trigonometry", "Calculus",
                    "Statistics", "Probability", "Mensuration",
                    "Number Systems", "Coordinate Geometry",
                    "Sets and Functions", "Sequences and Series"
            ),
            "Physics", List.of(
                    "Mechanics", "Thermodynamics", "Optics",
                    "Electricity", "Magnetism", "Modern Physics",
                    "Waves and Oscillations", "Gravitation", "Fluid Mechanics"
            ),
            "Chemistry", List.of(
                    "Atomic Structure", "Periodic Table", "Chemical Bonding",
                    "Acids and Bases", "Organic Chemistry", "Electrochemistry",
                    "Thermochemistry", "States of Matter", "Solutions",
                    "Coordination Chemistry"
            ),
            "Biology", List.of(
                    "Cell Biology", "Genetics", "Evolution", "Ecology",
                    "Human Physiology", "Plant Physiology",
                    "Biotechnology", "Microbiology", "Reproduction"
            ),
            "ComputerScience", List.of(
                    "Data Structures", "Algorithms", "Programming",
                    "Databases", "Computer Networks", "Operating Systems",
                    "Web Development", "Cybersecurity"
            ),
            "English", List.of(
                    "Reading Comprehension", "Grammar", "Writing",
                    "Literature", "Poetry", "Drama", "Vocabulary"
            ),
            "History", List.of(
                    "Ancient History", "Medieval History", "Modern History",
                    "World History", "Indian History", "Freedom Struggle"
            ),
            "Geography", List.of(
                    "Physical Geography", "Human Geography",
                    "Economic Geography", "Climatology", "Cartography",
                    "Indian Geography", "World Geography"
            ),
            "Economics", List.of(
                    "Microeconomics", "Macroeconomics", "International Trade",
                    "Public Finance", "Money and Banking", "Statistics"
            ),
            "GeneralKnowledge", List.of(
                    "Current Affairs", "Static GK", "Science and Tech",
                    "Sports", "Awards", "Books and Authors"
            )
    );

    /** Returns the topic list for a subject, or empty if subject not known. */
    public static List<String> topicsFor(String subject) {
        return TOPICS.getOrDefault(subject, List.of("General"));
    }

    /** Get all subjects we support. */
    public static List<String> allSubjects() {
        return List.copyOf(TOPICS.keySet());
    }
}