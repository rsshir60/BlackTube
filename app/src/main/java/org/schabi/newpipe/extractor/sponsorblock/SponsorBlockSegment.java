package org.schabi.newpipe.extractor.sponsorblock;

public class SponsorBlockSegment {
    public String uuid;
    public SponsorBlockCategory category;
    public SponsorBlockAction action;
    public float startTime;
    public float endTime;
    public float segmentStart;
    public float segmentEnd;

    public SponsorBlockSegment() {}

    public SponsorBlockSegment(String uuid, float startTime, float endTime, SponsorBlockCategory category, SponsorBlockAction action) {
        this.uuid = uuid;
        this.category = category;
        this.action = action;
        this.startTime = startTime;
        this.endTime = endTime;
        this.segmentStart = startTime;
        this.segmentEnd = endTime;
    }

    public SponsorBlockCategory getCategory() { return category; }
    public float getSegmentStart() { return segmentStart; }
    public float getSegmentEnd() { return segmentEnd; }
}
