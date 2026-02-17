package com.courttrack.update;

public class UpdateInfo {
    private final String version;
    private final String downloadUrl;
    private final String releaseNotes;
    private final long fileSize;

    public UpdateInfo(String version, String downloadUrl, String releaseNotes, long fileSize) {
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.releaseNotes = releaseNotes;
        this.fileSize = fileSize;
    }

    public String getVersion() { return version; }
    public String getDownloadUrl() { return downloadUrl; }
    public String getReleaseNotes() { return releaseNotes; }
    public long getFileSize() { return fileSize; }
}
