package cn.crap.controller.thirdly;

import java.util.List;

import cn.crap.enumeration.UserStatus;
import cn.crap.enumeration.UserType;
import cn.crap.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import cn.crap.dto.LoginDto;
import cn.crap.dto.thirdly.GitHubUser;
import cn.crap.enumeration.LoginType;
import cn.crap.framework.base.BaseController;
import cn.crap.service.IMenuService;
import cn.crap.service.IUserService;
import cn.crap.model.User;
import cn.crap.service.imp.thirdly.GitHubService;
import cn.crap.springbeans.Config;

/**
 * 前后台共用的Controller
 * @author Ehsan
 *
 */
@Controller
public class GitHubController extends BaseController<User> {
	@Autowired
	IMenuService menuService;
	@Autowired
	private Config config;
	@Autowired
	private GitHubService githHubService;
	@Autowired
	private IUserService userService;
	
	
	/**
	 * gitHub授权
	 * @throws Exception
	 */
	@RequestMapping("/github/authorize.do")
	public void authorize() throws Exception {
		String authorizeUrl = "https://github.com/login/oauth/authorize?client_id=%s&state=%s";
		String state = Tools.getChar(20);
		cacheService.setStr( MyCookie.getCookie(Const.COOKIE_TOKEN, false, request) + Const.CACHE_AUTHORIZE, state, 10*60);
		response.sendRedirect(String.format(authorizeUrl, config.getClientID(), state));
	}
	@RequestMapping("/github/login.do")
	public String login(@RequestParam String code,@RequestParam String state) throws Exception {
		String myState = cacheService.getStr(MyCookie.getCookie(Const.COOKIE_TOKEN, false, request) + Const.CACHE_AUTHORIZE);
		if(myState == null || !myState.equals(state)){
			request.setAttribute("result", "非法参数，登陆失败！");
			return "WEB-INF/views/result.jsp";
		}else{
			User user = null;
			GitHubUser gitHubUser = githHubService.getUser(githHubService.getAccessToken(code, "").getAccess_token());
			List<User> users = userService.findByMap(Tools.getMap(TableField.USER.THIRDLY_ID, getThirdlyId(gitHubUser)), null, null);
			if(users.size() == 0){
				user = new User();
				user.setUserName( Tools.handleUserName(gitHubUser.getLogin()) );
				user.setTrueName(gitHubUser.getName());

				// 登陆用户类型&邮箱有唯一约束，同一个邮箱在同一个登陆类型下不允许绑定两个账号
				if(!MyString.isEmpty(gitHubUser.getEmail())){
					String email = gitHubUser.getEmail();
					List<User> existUser = userService.findByMap(Tools.getMap(TableField.USER.EMAIL, email, TableField.USER.USER_TYPE, LoginType.GITHUB.getValue()), null, null);
					if (existUser == null || existUser.size() == 0){
						user.setEmail(gitHubUser.getEmail());
					}
				}

				user.setPassword("");
				user.setStatus(UserStatus.INVALID.getType());
				user.setType(UserType.USER.getType());
				user.setAvatarUrl(gitHubUser.getAvatar_url());
				user.setThirdlyId(getThirdlyId(gitHubUser));
				user.setLoginType(LoginType.GITHUB.getValue());
				userService.save(user);
			}else{
				user = users.get(0);
			}
			
			// 登陆
			LoginDto model = new LoginDto();
			model.setUserName(user.getUserName());
			model.setRemberPwd("NO");
			userService.login(model, user, request, response);
			
			response.sendRedirect("../admin.do");
		}
		return "";
	}

	private String getThirdlyId(GitHubUser gitHubUser) {
		return Const.GITHUB + gitHubUser.getId();
	}
}
