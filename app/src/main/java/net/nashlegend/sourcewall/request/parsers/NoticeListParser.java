package net.nashlegend.sourcewall.request.parsers;

import net.nashlegend.sourcewall.model.Notice;
import net.nashlegend.sourcewall.request.JsonHandler;
import net.nashlegend.sourcewall.request.ResponseObject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by NashLegend on 16/5/2.
 */
public class NoticeListParser implements Parser<ArrayList<Notice>> {
    @Override
    public ArrayList<Notice> parse(String response,
            ResponseObject<ArrayList<Notice>> responseObject) throws Exception {
        JSONArray notices = JsonHandler.getUniversalJsonArray(response, responseObject);
        if (notices == null) throw new NullPointerException("notices is null");
        ArrayList<Notice> noticeList = new ArrayList<>();
        for (int i = 0; i < notices.length(); i++) {
            JSONObject noticesObject = notices.getJSONObject(i);
            Notice notice = Notice.fromJson(noticesObject);
            noticeList.add(notice);
        }
        return noticeList;
    }
}
