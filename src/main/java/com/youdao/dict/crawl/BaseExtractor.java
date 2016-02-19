package com.youdao.dict.crawl;

import cn.edu.hfut.dmic.webcollector.model.Page;
import cn.edu.hfut.dmic.webcollector.util.JsoupUtils;
import com.youdao.dict.bean.ParserPage;
import com.youdao.dict.score.LeveDis;
import com.youdao.dict.souplang.Context;
import com.youdao.dict.util.GFWHelper;
import com.youdao.dict.util.OImageConfig;
import com.youdao.dict.util.OImageUploader;
import lombok.extern.apachecommons.CommonsLog;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by liuhl on 15-8-17.
 */
@CommonsLog
public class BaseExtractor {
    Context context;
    String keywords;
    public static long MINSIZE = 384;
    public ParserPage p = new ParserPage();
    String url;
    Document doc;
    Element content;
    List<ParserPage> parserPages = new ArrayList<ParserPage>();


    public ParserPage getParserPage() {
        return p;
    }

    public BaseExtractor(Page page) {
        url = page.getUrl();
        doc = page.getDoc();
        JsoupUtils.makeAbs(doc, url);
        p.setHost(getHost(url));
        p.setUrl(url);
    }

    public BaseExtractor(String url) {
        try {
            this.doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.url = url;
        JsoupUtils.makeAbs(doc, url);
        p.setHost(getHost(url));
        p.setUrl(url);
    }


    public boolean extractor() {
        if (init())
            return extractorTime() && extractorTitle() && extractorType() && extractorAndUploadImg() && extractorDescription() && extractorContent() && extractorKeywords() && extractorTags(keywords, p.getLabel());
        else
            return false;
    }

    public boolean init() {
        return false;
    }

    public boolean extractorTitle() {
        return false;
    }

    public boolean extractorType() {
        return false;
    }

    public boolean extractorTime() {
        return false;
    }

    public boolean extractorKeywords() {
        log.debug("*****extractorKeywords*****");
        Element keywordsElement = (Element) context.output.get("keywords");
        if (keywordsElement == null)
            return true;
        keywords = keywordsElement.attr("content");
        if (keywords == null || "".equals(keywords)) {
            return true;
        }
        if (!keywords.contains(",")) {
            keywords = "".equals(keywords) ? "" : keywords.replaceAll(" ", ",");
        }
        return true;
    }

    public boolean isPaging() {
        return false;
    }

    public boolean extractorAndUploadImg() {
        return extractorAndUploadImg("", "");
    }

    public boolean extractorAndUploadImg(String host, String port) {
        log.debug("*****extractorAndUploadImg*****");
        if (content == null || p == null) {
            return false;
        }
       /* if (host.equals(port)) return true;*/
        String mainImage = null;
        int width = 0;
        try {
            Elements imgs = content.select("img");
            for (Element img : imgs) {
                String imageUrl = img.attr("src");
                img.removeAttr("width");
                img.removeAttr("WIDTH");
                img.removeAttr("height");
                img.removeAttr("HEIGHT");
//                img.attr("style", "width:100%;");
                OImageUploader uploader = new OImageUploader();
                if (!"".equals(host) && !"".equals(port))
                    uploader.setProxy(host, port);
                long id = uploader.deal(imageUrl);
                URL newUrl = new OImageConfig().getImageSrc(id, "dict-consult");
                int twidth = uploader.getWidth();
                if(twidth >= 300)
                    img.attr("style", "width:100%;");
                img.attr("src", newUrl.toString());
                if (mainImage == null) {
                    width = uploader.getWidth();
                    mainImage = newUrl.toString();
                }
            }


        } catch (Exception e) {
            p.setStyle("no-image");
        }
        p.setMainimage(mainImage);
        if (width == 0) {
            p.setStyle("no-image");
        } else if (width > 300) {
            p.setStyle("large-image");
        } else {
            p.setStyle("no-image");
        }
        return true;
    }

    public boolean extractorDescription() {
        return true;
    }

    public boolean extractorContent() {
        return extractorContent(false);
    }


    public boolean extractorContent(boolean paging) {
        log.debug("*****extractorContent*****");
        if (content == null || p == null || (!paging && content.text().length() < MINSIZE)) {
            return false;
        }
        Elements hypLinks = content.select("a");
        for(Element a: hypLinks){
            a.unwrap();
//            System.out.println(a);
        }

        removeComments(content);

        String contentHtml = content.html();

        contentHtml = contentHtml.replaceAll("&gt;", ">").replaceAll("&lt;", "<");//替换转义字符

        contentHtml = contentHtml.replaceAll("(?i)(<SCRIPT)[\\s\\S]*?((</SCRIPT>)|(/>))", "");//去除script
        contentHtml = contentHtml.replaceAll("(?i)(<NOSCRIPT)[\\s\\S]*?((</NOSCRIPT>)|(/>))", "");//去除NOSCRIPT
        contentHtml = contentHtml.replaceAll("(?i)(<STYLE)[\\s\\S]*?((</STYLE>)|(/>))", "");//去除style
        contentHtml = contentHtml.replaceAll("<(?!img|br|p[ >]|/p).*?>", "");//去除所有标签，只剩img,br,p
        contentHtml = contentHtml.replaceAll("\\\\s*|\\t|\\r|\\n", "");//去除换行符制表符/r,/n,/t /n
//        contentHtml = contentHtml.replaceAll("(\\n[\\s]*?)+", "\n");//多个换行符 保留一个----意义不大，本来也显示不出来，还是加<p>达到换行效果


        if(contentHtml.length() < 384) return false;//太短

        p.setContent(contentHtml);
        if (!paging && isPaging()) {
            mergePage(p);
        }
        log.debug("*****extractorContent  success*****");
        return true;
    }

    public  void replaceFrame(){
        Elements videos = content.select("iframe");
        for(Element e: videos){
            String videoUrl = e.attr("src");
            if(GFWHelper.isBlocked(videoUrl))
                continue;
            String className = e.className();
            Tag imgTag = Tag.valueOf("p");
//                img.appendChild(imgTag);
            Element newImg = new Element(imgTag, "");
            newImg.attr("class", "iframe");
            newImg.attr("src", videoUrl);
            newImg.attr("style", "width:100%; heigh:100%");
            newImg.attr("heigh","200");
            e.appendChild(newImg);
        }
    }

    public String resumeFrame(String orgContent){
        Document extractedContent = Jsoup.parse(orgContent);
//        for(String className: classNames){
        Elements videoClassNames = extractedContent.select(".iframe");
        for(Element e: videoClassNames){
            String videoUrl = e.attr("src");
            Tag imgTag = Tag.valueOf("iframe");
//                img.appendChild(imgTag);
            Element newImg = new Element(imgTag, "");
            newImg.attr("src", videoUrl);
            newImg.attr("style", "width:100%");
//                newImg.a
            e.appendChild(newImg);
            e.unwrap();
        }
        return extractedContent.body().html();
    }

    public static void removeComments(Node node) {
        for (int i = 0; i < node.childNodes().size();) {
            Node child = node.childNode(i);
            if (child.nodeName().equals("#comment"))
                child.remove();
            else {
                removeComments(child);
                i++;
            }
        }
    }

    public void mergePage(ParserPage p) {
    }

    public boolean extractorTags(String... keywords) {
        log.debug("*****extractorTags*****");
        if (content == null) {
            log.info("*****extractorTags  failed***** url:" + url);
            return false;
        }
        try {
            String contentStr = content.text();
            LeveDis leveDis = LeveDis.getInstance("");
            String tags = leveDis.tag(contentStr, 5);
            if (keywords != null) {
                for (String key : keywords) {
                    if ("".equals(key) || key == null) {
                        continue;
                    }
                    if (!"".equals(tags) && !tags.contains(key)) {
                        tags = key + "," + tags;
                    } else {
                        tags = key;
                    }
                }
            }
            p.setLabel(tags);
            int level = leveDis.compFileLevel(leveDis.compLevel(contentStr));
            p.setLevel(String.valueOf(level));
            log.debug("*****extractorTags  success*****");
            return true;
        } catch (Exception e) {
            log.info("*****extractorTags  failed***** url:" + url);
            return false;
        }
    }

    public static String getHost(String url) {
        if (url == null || url.trim().equals("")) {
            return "";
        }
        String host = "";
        Pattern p = Pattern.compile("(?<=//|)((\\w)+\\.)+\\w+");
        Matcher matcher = p.matcher(url);
        if (matcher.find()) {
            host = matcher.group();
        }
        return host;
    }

    public List<ParserPage> getParserPageList() {
        return parserPages;
    }
}
