package com.taoyuanx.ca.web.controller;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.rowset.serial.SerialException;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.taoyuanx.ca.openssl.cert.CertUtil;
import com.taoyuanx.ca.web.anno.NeedToken;
import com.taoyuanx.ca.web.common.CAConstant.KeyType;
import com.taoyuanx.ca.web.common.Result;
import com.taoyuanx.ca.web.common.ResultBuilder;
import com.taoyuanx.ca.web.config.AppConfig;
import com.taoyuanx.ca.web.dto.CertReq;
import com.taoyuanx.ca.web.dto.CertResult;
import com.taoyuanx.ca.web.dto.KeyPairResult;
import com.taoyuanx.ca.web.service.CertService;
import com.taoyuanx.ca.web.util.SimpleTokenManager;
import com.taoyuanx.ca.web.util.Validator;

import cn.hutool.core.util.ZipUtil;

@RestController
@RequestMapping(value="api")
@NeedToken(isNeed=true)
public class ApiController {
	@Autowired
	AppConfig appConfig;
	@Autowired
	SimpleTokenManager tokenManager;
	@Autowired
	CertService certService;
    /**
     * 登录验证
     * @param param
     * @return
     * @throws Exception
     */
    @RequestMapping(value="login",method=RequestMethod.POST)
    @NeedToken(isNeed=false)
	public Result<JSONObject> login(@RequestBody JSONObject param) throws Exception{
		String username=param.getString("username");
		String password=param.getString("password");
		Validator.validator()
		.isEqueal(appConfig.getUsername(), username, "账户信息不匹配")
		.isEqueal(appConfig.getPassword(), password, "账户信息不匹配");
		String token=tokenManager.createToken(new HashMap<String,String>(),30L,TimeUnit.MINUTES);
		JSONObject result=new JSONObject();
		result.put("token", token);
		result.put("username", username);
		return ResultBuilder.buildOkResult(result);
	}
    
	
	/**
	 * 公私钥对生成
	 * @param param
	 * @return
	 * @throws Exception
	 */
    @RequestMapping(value="create",method=RequestMethod.POST)
	public Result<JSONObject> create(@RequestBody JSONObject param) throws Exception{
		String type=param.getString("type");
		String keySize=param.getString("keySize");
		if((type.equals("1")||type.equals("4"))&& keySize==null) {
			keySize="1024";
			Validator.validator().isMatch(Pattern.compile("^[1024|2048]$"), type,"密钥位数不支持");
		}
		Validator.validator().isMatch(Pattern.compile("^[1|2|3|4]$"), type,"密钥对类型不支持");
		JSONObject result=new JSONObject();
		
		Integer ks=keySize==null?null:Integer.parseInt(keySize);
		KeyPairResult createKeyPair = certService.createKeyPair(KeyType.forValue(Integer.parseInt(type)), ks);
		result.put("pub", createKeyPair.getPub_pem());
		result.put("pri", createKeyPair.getPri_pem());
		return ResultBuilder.buildOkResult(result);
	}
    
    
	/**
	 * 公私钥对生成
	 * @param param
	 * @return
	 * @throws Exception
	 */
    @RequestMapping(value="cert",method=RequestMethod.POST)
	public Result<JSONObject> cert(@RequestBody CertReq certReq) throws Exception{
    	Integer keySize=certReq.getKeySize()==null?null:Integer.parseInt(certReq.getKeySize());
    	String serialNumber=String.valueOf(System.currentTimeMillis());
		certReq.setSerialNumber(serialNumber);
		CertResult certToUser = certService.certToUser(certReq, keySize);
		String certDir=appConfig.getClientCertBasePath()+"/"+serialNumber;
		String pri_pem_path=certDir+"/client_pri.pem";
		String pub_pem_path=certDir+"/client_pub.pem";
		String cert_path=certDir+"/client_cert.cer";
		String p12_path=certDir+"/client.p12";
		String userAlias="taoyuanx-client";
		String password="123456";
		
		FileUtils.forceMkdir(new File(certDir));
		CertUtil.savePrivateKeyPem(certToUser.getPri(), pri_pem_path);
		CertUtil.savePublicKeyPem(certToUser.getPub(), pub_pem_path);
		CertUtil.saveX509CertBase64(certToUser.getCert(), cert_path);
		CertUtil.savePKCS12(certToUser.getCert(), certToUser.getPri(), userAlias, password, p12_path);
		JSONObject result=new JSONObject();
		result.put("cert_path", cert_path);
		result.put("userAlias", userAlias);
		result.put("p12_path", p12_path);
		result.put("password", password);
		result.put("pri", FileUtils.readFileToString(new File(pri_pem_path)));
		result.put("pub", FileUtils.readFileToString(new File(pub_pem_path)));
		result.put("serialNumber", serialNumber);
		return ResultBuilder.buildOkResult(result);
	}
    
	@RequestMapping(value="downCertZip",method=RequestMethod.GET)
	public void downCertZip(String serialNumber, HttpServletResponse resp, HttpServletRequest req) throws SerialException {
		try {
			String certDir=appConfig.getClientCertBasePath()+"/"+serialNumber;
			String destPath=appConfig.getClientCertBasePath()+"/"+UUID.randomUUID().toString()+".zip";
			String readmeDoc=certDir+"/readme.txt";
			String readmeTxt="p12 alias:taoyuanx-client\r\n p12 密码:123456";
			FileUtils.writeStringToFile(new File(readmeDoc), readmeTxt,"UTF-8");
			ZipUtil.zip(certDir,destPath);
			// 下载
			File destZipFile=new File(destPath);
			resp.setContentType(req.getServletContext().getMimeType(destZipFile.getName()));
			resp.setHeader("Content-type", "application/octet-stream");
			resp.setHeader("Content-Disposition",
					"attachment;fileName="+new String("cert.zip".getBytes(),"UTF-8"));
			resp.getOutputStream().write(FileUtils.readFileToByteArray(destZipFile));
	
		} catch (Exception e) {
			throw new SerialException("下载失败");
		}
	}
    
    /**
	 * 公私钥对生成
	 * @param param
	 * @return
	 * @throws Exception
	 */
    @RequestMapping(value="dyFrom",method=RequestMethod.GET)
	public Result<JSONObject> dyFrom(String  dyType) throws Exception{
    	JSONObject result=new JSONObject();
    	String html="";
	    result.put("html", html);
		return ResultBuilder.buildOkResult(result);
	}
    
    
}
