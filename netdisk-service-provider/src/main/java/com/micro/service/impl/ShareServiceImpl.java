package com.micro.service.impl;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.alibaba.dubbo.config.annotation.Service;
import com.micro.chain.core.Bootstrap;
import com.micro.chain.core.HandlerInitializer;
import com.micro.chain.core.Pipeline;
import com.micro.chain.handler.ShareCancelFriendHandler;
import com.micro.chain.handler.ShareCancelSecretHandler;
import com.micro.chain.handler.ShareCancelUpdateHandler;
import com.micro.chain.handler.ShareCancelValidateHandler;
import com.micro.chain.handler.ShareFriendsFromAlbumHandler;
import com.micro.chain.handler.ShareFriendsFromFileHandler;
import com.micro.chain.handler.ShareFriendsNoticeHandler;
import com.micro.chain.handler.ShareFriendsSaveFriendsHandler;
import com.micro.chain.handler.ShareFriendsSaveHandler;
import com.micro.chain.handler.ShareFriendsValidateHandler;
import com.micro.chain.handler.ShareSecretDetailFromAlbumHandler;
import com.micro.chain.handler.ShareSecretDetailFromFileHandler;
import com.micro.chain.handler.ShareSecretRedisHandler;
import com.micro.chain.handler.ShareSecretSaveHandler;
import com.micro.chain.handler.ShareSecretValidateHandler;
import com.micro.chain.param.ShareCancelRequest;
import com.micro.chain.param.ShareFriendsRequest;
import com.micro.chain.param.ShareSecretRequest;
import com.micro.chain.param.ShareSecretResponse;
import com.micro.common.Contanst;
import com.micro.common.DateUtils;
import com.micro.common.ValidateUtils;
import com.micro.db.dao.DiskShareDao;
import com.micro.db.jdbc.DiskShareFileJdbc;
import com.micro.db.jdbc.DiskShareFriendsJdbc;
import com.micro.db.jdbc.DiskShareJdbc;
import com.micro.disk.bean.PageInfo;
import com.micro.disk.bean.ShareBean;
import com.micro.disk.bean.ShareFileBean;
import com.micro.disk.bean.ShareFriendsBean;
import com.micro.disk.bean.ShareSecretResult;
import com.micro.disk.service.ShareService;
import com.micro.model.DiskShare;
import com.micro.utils.SpringContentUtils;

@Service(interfaceClass=ShareService.class)
@Component
@Transactional
public class ShareServiceImpl implements ShareService{
	@Autowired
	private DiskShareDao diskShareDao;
	@Autowired
	private DiskShareJdbc diskShareJdbc;
	@Autowired
	private DiskShareFileJdbc diskShareFileJdbc;
	@Autowired
	private StringRedisTemplate stringRedisTemplate;
	@Autowired
	private DiskShareFriendsJdbc diskShareFriendsJdbc;
	@Autowired
	private SpringContentUtils springContentUtils;
	
	@Override
	public ShareSecretResult shareSecret(List<String> ids,String title, String userid, String username, Integer sharetype,Integer effect,Integer type) {
		ShareSecretRequest request=new ShareSecretRequest();
		request.setIds(ids);
		request.setTitle(title);
		request.setUserid(userid);
		request.setUsername(username);
		request.setSharetype(sharetype);
		request.setEffect(effect);
		request.setType(type);
		
		Bootstrap bootstrap=new Bootstrap();
		bootstrap.childHandler(new HandlerInitializer(request,new ShareSecretResponse()) {
			@Override
			protected void initChannel(Pipeline pipeline) {
				//1.????????????
				pipeline.addLast(springContentUtils.getHandler(ShareSecretValidateHandler.class));
				//2.??????disk_share
				pipeline.addLast(springContentUtils.getHandler(ShareSecretSaveHandler.class));
				//3.??????disk_share_file??????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareSecretDetailFromAlbumHandler.class));
				//4.??????disk_share_file??????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareSecretDetailFromFileHandler.class));
				//5.??????Redis????????????
				pipeline.addLast(springContentUtils.getHandler(ShareSecretRedisHandler.class));
				//6.????????????
			}
		});
		ShareSecretResponse res=(ShareSecretResponse) bootstrap.execute();
		
		ShareSecretResult result=new ShareSecretResult();
		result.setUrl(res.getUrl());
		result.setCode(res.getCode());
		return result;
	}
	
	@Override
	public void shareFriends(List<String> ids,List<ShareFriendsBean> friends,String title,String userid,String username,Integer type) {
		ShareFriendsRequest request=new ShareFriendsRequest();
		request.setIds(ids);
		request.setFriends(friends);
		request.setTitle(title);
		request.setUserid(userid);
		request.setUsername(username);
		request.setType(type);
		
		Bootstrap bootstrap=new Bootstrap();
		bootstrap.childHandler(new HandlerInitializer(request,null) {
			@Override
			protected void initChannel(Pipeline pipeline) {
				//1.????????????
				pipeline.addLast(springContentUtils.getHandler(ShareFriendsValidateHandler.class));
				//2.??????disk_share
				pipeline.addLast(springContentUtils.getHandler(ShareFriendsSaveHandler.class));
				//3.??????disk_share_friends
				pipeline.addLast(springContentUtils.getHandler(ShareFriendsSaveFriendsHandler.class));
				//4.??????disk_share_file??????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareFriendsFromAlbumHandler.class));
				//5.??????disk_share_file??????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareFriendsFromFileHandler.class));
				//6.???????????????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareFriendsNoticeHandler.class));
				//7.????????????
			}
		});
		bootstrap.execute();
	}
	
	@Override
	public ShareBean findShareInfo(String id) {
		ValidateUtils.validate(id, "??????ID");
		id=id.toLowerCase();
		DiskShare share=diskShareDao.findOne(id);
		if(share==null){
			throw new RuntimeException("?????????????????????");
		}
		ShareBean sb=new ShareBean();
		sb.setId(share.getId());
		sb.setTitle(share.getTitle());
		sb.setShareuser(share.getShareusername());
		sb.setSharetime(DateUtils.formatDate(share.getSharetime(),"yyyy-MM-dd HH:mm:ss"));
		
		String effectname="";
		if(share.getEffect()==0){
			effectname="????????????";
		}else {
			effectname=share.getEffect()+"?????????";
		}
		sb.setEffectname(effectname);
		sb.setStatus(share.getStatus());
		sb.setSharetype(share.getSharetype());
		return sb;
	}
	@Override
	public String validateCode(String id, String code) {
		ValidateUtils.validate(id, "??????ID");
		ValidateUtils.validate(code, "?????????");
		id=id.toLowerCase();
		DiskShare share=diskShareDao.findOne(id);
		if(share==null){
			throw new RuntimeException("?????????????????????");
		}
		if(share.getType()!=0){
			throw new RuntimeException("????????????????????????");
		}
		if(share.getSharetype()!=0){
			throw new RuntimeException("??????????????????");
		}
		if(!share.getCode().equals(code)){
			throw new RuntimeException("??????????????????");
		}
		if(share.getEffect()!=0){
			if(share.getEndtime().before(new Date())){
				throw new RuntimeException("?????????????????????");
			}
		}
		if(share.getStatus()==1){
			throw new RuntimeException("?????????????????????");
		}
		if(share.getStatus()==2){
			throw new RuntimeException("???????????????????????????");
		}
		
		String token=UUID.randomUUID().toString();
		if(share.getType()==0&&share.getSharetype()==0){
			//???????????????????????????????????????token
			stringRedisTemplate.opsForValue().set(Contanst.PREFIX_SHARE_CODE+token, token, 10, TimeUnit.MINUTES);
			return token;
		}
		return "";
	}
	@Override
	public List<ShareFileBean> findShareFileListFromSecret(String id,String pid,String token) {
		ValidateUtils.validate(id, "??????ID");
		if(StringUtils.isEmpty(pid)){
			pid="0";
		}
		id=id.toLowerCase();
		DiskShare share=diskShareDao.findOne(id);
		if(share==null){
			throw new RuntimeException("?????????????????????");
		}
		
		//??????????????????????????????????????????token
		if(share.getType()==0&&share.getSharetype()==0){
			String value=stringRedisTemplate.opsForValue().get(Contanst.PREFIX_SHARE_CODE+token);
			if(StringUtils.isEmpty(value)){
				throw new RuntimeException("????????????????????????????????????????????????,???????????????!");
			}else{				
				stringRedisTemplate.expire(Contanst.PREFIX_SHARE_CODE+token, 10, TimeUnit.MINUTES);
			}
		}
		//????????????????????????
		if(share.getStatus()==1){
			throw new RuntimeException("??????????????????");
		}
		//????????????????????????
		if(share.getStatus()==2){
			throw new RuntimeException("??????????????????");
		}
		
		//????????????
		return diskShareFileJdbc.findListChild(id, pid);
	}
	@Override
	public List<ShareFileBean> findShareFileListFromFriends(String id,String pid) {
		ValidateUtils.validate(id, "??????ID");
		if(StringUtils.isEmpty(pid)){
			pid="0";
		}
		DiskShare share=diskShareDao.findOne(id);
		if(share==null){
			throw new RuntimeException("?????????????????????");
		}
		if(share.getStatus()==1){
			throw new RuntimeException("??????????????????");
		}
		if(share.getStatus()==2){
			throw new RuntimeException("??????????????????");
		}
		
		//????????????
		return diskShareFileJdbc.findListChild(id, pid);
	}
	@Override
	public List<ShareFileBean> findShareFileListFromSelf(String id,String pid) {
		ValidateUtils.validate(id, "??????ID");
		if(StringUtils.isEmpty(pid)){
			pid="0";
		}
		return diskShareFileJdbc.findListChild(id, pid);
	}
	
	@Override
	public void validateShareIsEffect(String shareid) {
		ValidateUtils.validate(shareid, "??????ID");
		DiskShare share=diskShareDao.findOne(shareid);
		if(share==null){
			throw new RuntimeException("??????ID?????????");
		}
		if(share.getType()==0){//????????????			
			if(share.getEffect()!=0){
				if(share.getEndtime().before(new Date())){
					throw new RuntimeException("?????????????????????");
				}
			}
			if(share.getStatus()==1){
				throw new RuntimeException("?????????????????????");
			}
			if(share.getStatus()==2){
				throw new RuntimeException("???????????????????????????");
			}
		}
	}
	@Override
	public PageInfo<ShareBean> findMyShare(Integer page, Integer limit, String userid,Integer type,Integer status) {
		return diskShareJdbc.findMyShare(page, limit, userid,type,status);
	}
	
	@Override
	public PageInfo<ShareBean> findFriendsShare(Integer page, Integer limit, String userid, Integer status) {
		return diskShareJdbc.findFriendsShare(page, limit, userid, status);
	}
	
	@Override
	public void cancelShare(List<String> ids) {
		ShareCancelRequest request=new ShareCancelRequest();
		request.setIds(ids);
		
		Bootstrap bootstrap=new Bootstrap();
		bootstrap.childHandler(new HandlerInitializer(request,null) {
			@Override
			protected void initChannel(Pipeline pipeline) {
				//1.????????????
				pipeline.addLast(springContentUtils.getHandler(ShareCancelValidateHandler.class));
				//2.??????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareCancelUpdateHandler.class));
				//3.?????????????????????????????????????????????????????????Redis??????
				pipeline.addLast(springContentUtils.getHandler(ShareCancelSecretHandler.class));
				//4.????????????????????????????????????
				pipeline.addLast(springContentUtils.getHandler(ShareCancelFriendHandler.class));
			}
		});
		bootstrap.execute();
	}

	@Override
	public List<ShareFriendsBean> findFriends(String shareid) {
		
		return diskShareFriendsJdbc.findFriends(shareid);
	}

}
