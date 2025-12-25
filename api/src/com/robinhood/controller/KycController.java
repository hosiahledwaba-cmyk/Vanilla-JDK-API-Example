package com.robinhood.controller;
import com.robinhood.model.KycRequest; import com.robinhood.service.KycService;
import com.robinhood.db.KycRequestManager; import com.robinhood.util.*;
import java.util.Map;
public class KycController {
  public static String apply(String token,String body)throws Exception{
    String email=AuthManager.getEmailFromToken(token);
    var d=JSONParser.parse(body);
    KycRequest r=new KycRequest(0,email,d.get("fullName"),d.get("type"),d.get("url"),null,"pending",null,null);
    int id=KycRequestManager.create(r);
    var prov=KycService.submit(r);
    String provId=prov.get("request_id").toString();
    KycRequestManager.updateStatus(id,"pending",provId,null);
    return ResponseBuilder.json(201,"{\"requestId\":"+id+",\"providerId\":\""+provId+"\"}");
  }
  public static String status(String token,String body)throws Exception{
    var d=JSONParser.parse(body);
    int id=Integer.parseInt(d.get("requestId"));
    // fetch local record...
    var k=KycRequestManager.getById(id);
    var st=KycService.status(k.getProviderRequestId());
    KycRequestManager.updateStatus(id,st.get("status").toString(),k.getProviderRequestId(),st.get("note").toString());
    return ResponseBuilder.json(200,JSONParser.stringify(st));
  }
}