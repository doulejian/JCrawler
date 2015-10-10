package com.youdao.dict.crawl;

import cn.edu.hfut.dmic.webcollector.model.Page;
import com.youdao.dict.bean.ParserPage;
import com.youdao.dict.souplang.SoupLang;
import com.youdao.dict.util.OImageConfig;
import com.youdao.dict.util.OImageUploader;
import lombok.extern.apachecommons.CommonsLog;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

/**
 * Created by liuhl on 15-8-17.
 */
@CommonsLog
public class TheguardianExtractor extends BaseExtractor {

    public TheguardianExtractor(Page page) {
        super(page);
    }

    public TheguardianExtractor(String url) {
        super(url);
    }

    public boolean init() {
        log.debug("*****init*****");
        try {
            SoupLang soupLang = new SoupLang(SoupLang.class.getClassLoader().getResourceAsStream("TheguardianRule.xml"));
            context = soupLang.extract(doc);
            content = (Element) context.output.get("content");
            for(Element svg: content.select("svg")){
                if(svg != null) svg.remove();
            }
            content.select("span").remove();
            for(Element e: content.select("div")){
                String name = e.attr("class");
                if(name.contains("content__meta-container js-content-meta js-football-meta u-cf") ||
                        name.equals("submeta") ){
                    e.remove();
                }
            }
            content.removeClass("content__article-body from-content-api js-article__body");
            content.removeClass("meta__social");
            String isarticle = context.output.get("isarticle").toString();
            if(isarticle.contains("article")){
                log.debug("*****init  success*****");
                return true;
            }
            log.info("*****init  failed，isn't an article***** url:" + url);
            return false;
        } catch (Exception e) {
            log.info("*****init  failed***** url:" + url);
            return false;
        }
    }

    public boolean extractorTitle() {
        log.debug("*****extractorTitle*****");
//        String title = context.output.get("title").toString();
        Element elementTitle = (Element) context.output.get("title");
        if (elementTitle == null)
            return false;
        String title = elementTitle.attr("content");
        if (title == null || "".equals(title.trim())) {
            log.info("*****extractorTitle  failed***** url:" + url);
            return false;
        }
        title = title.replaceAll("\\\\s*|\\t|\\r|\\n", "");//去除换行符制表符/r,/n,/t
//        if (title.contains("-"))
//            p.setTitle(title.substring(0, title.lastIndexOf("-")).trim());
//        else
        p.setTitle(title.trim());
        log.debug("*****extractorTitle  success*****");
        return true;
    }

    public boolean extractorType() {
        Element elementType = (Element) context.output.get("type");
        if (elementType == null)
            return false;
        String type = elementType.attr("content");
        if (type == null || "".equals(type.trim())) {
            log.info("*****extractorTitle  failed***** url:" + url);
            return false;
        }
        if (type.contains("/")) {
            type = type.substring(0, type.indexOf("/"));
            type = type.replace("/", "");
        }
        p.setType(type.trim());

        Element elementLabel = (Element) context.output.get("label");
        if (elementLabel == null)
            return false;
        String label = elementLabel.attr("content");
//        String label = (String) context.output.get("label");
        if (label == null || "".equals(label.trim())) {
            log.info("*****extractorLabel  failed***** url:" + url);
            return false;
        }
        label = label.contains("China")?"China":label.contains("news")? "World": label;//news belong to World
        p.setLabel(label);
        log.debug("*****extractorTitle  success*****");
        return true;
    }

    public boolean extractorTime() {
        log.debug("*****extractorTime*****");
        Element elementTime = (Element) context.output.get("time");
        if (elementTime == null)
            return false;
        String time = elementTime.attr("content");
        if (time == null || "".equals(time.trim())) {
            log.info("*****extractorTime  failed***** url:" + url);
            return false;
        }
//2015-09-12 08:25
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'");
        Date date;
        try {
            date = format.parse(time.trim());
        } catch (ParseException e) {
            return false;
        }
        if (System.currentTimeMillis() - date.getTime() > 7 * 24 * 60 * 60 * 1000) {
            log.debug("*****extractorTime  out of date*****");
            return false;
        }
        p.setTime(new Timestamp(date.getTime() + 8 * 60 * 60 * 1000).toString());//utc 2 cst北京时间
        log.debug("*****extractorTime  success*****");
        return true;
    }

    public boolean isPaging() {
        Elements div = doc.select("div[id=div_currpage]");
        if (div == null) {
            return false;
        }
        Elements a = div.select("a");
        if (a == null || a.size() == 0) {
            return false;
        }
/*        if (url.equals(a.get(0).attr("href"))) {
            return false;
        }*/
        return true;
    }

    public boolean extractorAndUploadImg(String host, String port) {
        log.debug("*****extractorAndUploadImg*****");
        if (content == null || p == null) {
            return false;
        }
       /* if (host.equals(port)) return true;*/
        try {
            Elements imgs = content.select("img");
            String mainImage = null;
            int width = 0;
            for (Element img : imgs) {
                String imageUrl = img.attr("src");
                if ("".equals(imageUrl) || !"".equals(img.attr("data-src-small")) || !"".equals(img.attr("itemprop"))) {
                    img.remove();
                    continue;
                }
                img.removeAttr("width");
                img.removeAttr("WIDTH");
                img.removeAttr("height");
                img.removeAttr("HEIGHT");
                img.attr("style", "width:100%;");
                OImageUploader uploader = new OImageUploader();
                if (!"".equals(host) && !"".equals(port))
                    uploader.setProxy(host, port);
                long id = uploader.deal(imageUrl);
//                long id = 0;
                URL newUrl = new OImageConfig().getImageSrc(id, "dict-consult");
                img.attr("src", newUrl.toString());
                if (mainImage == null) {
                    width = uploader.getWidth();
                    mainImage = newUrl.toString();
                }
            }
            if(mainImage == null) {
                Element elementImg = (Element) context.output.get("mainimage");
                if (elementImg == null)
                    return false;
                mainImage = elementImg.attr("content");
                OImageUploader uploader = new OImageUploader();
                if (!"".equals(host) && !"".equals(port))
                    uploader.setProxy(host, port);
                long id = uploader.deal(mainImage);
//                long id = 0;
                URL newUrl = new OImageConfig().getImageSrc(id, "dict-consult");
                width = uploader.getWidth();
                mainImage = newUrl.toString();
            }
            p.setMainimage(mainImage);
            if (width == 0) {
                p.setStyle("no-image");
            } else if (width > 300) {
                p.setStyle("large-image");
            } else {
                p.setStyle("mini-image");
            }

        } catch (Exception e) {
            p.setStyle("no-image");
        }
        return true;

    }
}
