package org.schabi.newpipe.extractor.channel;

import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

public class ChannelTabInfo extends org.schabi.newpipe.extractor.ListInfo<org.schabi.newpipe.extractor.InfoItem> {
    public ChannelTabInfo(int serviceId, String url, String name) { super(serviceId, new org.schabi.newpipe.extractor.linkhandler.ListLinkHandler(url, url, "", java.util.Collections.emptyList(), ""), name); }
    private String name;
    private ListLinkHandler linkHandler;

    public ChannelTabInfo(String name, org.schabi.newpipe.extractor.linkhandler.ListLinkHandler linkHandler) { super(0, linkHandler, name);
        this.name = name;
        this.linkHandler = linkHandler;
    }

    public String getName() {
        return name;
    }

    public ListLinkHandler getLinkHandler() {
        return linkHandler;
    }
    public static ChannelTabInfo getInfo(org.schabi.newpipe.extractor.StreamingService service, ListLinkHandler handler) throws org.schabi.newpipe.extractor.exceptions.ExtractionException { return new ChannelTabInfo("stub", handler); }
    public static org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage<org.schabi.newpipe.extractor.InfoItem> getMoreItems(org.schabi.newpipe.extractor.StreamingService service, org.schabi.newpipe.extractor.linkhandler.ListLinkHandler handler, org.schabi.newpipe.extractor.Page page) { return null; }
}



