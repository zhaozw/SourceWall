package net.nashlegend.sourcewall.request.parsers;

import net.nashlegend.sourcewall.request.JsonHandler;
import net.nashlegend.sourcewall.request.ResponseObject;

/**
 * Created by NashLegend on 2015/10/6 0006.
 * 简单的判断是否返回code为0，只要是0，就会进入onResponse，否则进入onFailure.
 * 因此onResponse处肯定为true
 */
public class BooleanParser implements Parser<Boolean> {
    @Override
    public Boolean parse(String str, ResponseObject<Boolean> responseObject) throws Exception {
        return JsonHandler.getUniversalJsonSimpleBoolean(str, responseObject);
    }
}
