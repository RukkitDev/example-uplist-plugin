package cn.rukkit.uplistplugin;

import cn.rukkit.Rukkit;
import cn.rukkit.command.ServerCommand;
import cn.rukkit.command.ServerCommandListener;
import cn.rukkit.plugin.RukkitPlugin;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.util.Base64;
import okhttp3.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;

public class MainPlugin extends RukkitPlugin {

	private Logger log;
	private String addData;
	private String updateData;
	private String removeData;
	private String sid;
	private String selfCheckData;
	private boolean isPublished = false;
	private boolean upState = false;
	private ScheduledFuture updateFuture;
	
	private class UpdateTask implements Runnable {
		@Override
		public void run() {
			updateServer();
		}
	}

	public class PublishServerCommandListener implements ServerCommandListener {
		@Override
		public void onSend(String[] strings) {
			if (strings.length == 0) {
				System.out.println("== 列表插件帮助 == \n" +
						"publish start 启动列表公开\n" +
						"publish stop 关闭列表公开\n" +
						"publish motd <内容> 设置服务器motd\n" +
						"publish state 查看公开状态");
				return;
			}
			switch (strings[0]) {
				case "start":
					if (isPublished) {
						System.out.println("服务器已经公开！");
					} else {
						addServer();
					}
					break;
				case "stop":
					if (!isPublished) {
						System.out.println("公开状态已经关闭");
					} else {
						stopServer();
					}
					break;
				case "motd":
					if (strings.length > 1) {
						Rukkit.getConfig().serverMotd = strings[1];
						System.out.println("更改成功！");
						if (isPublished) updateServer();
					} else {
						System.out.println("请填写服务器motd！");
					}
					break;
				case "state":
					System.out.println("== 列表公开状态 ==\n" +
							"服务状态: " + (isPublished ? "已启动" : "未启动") + "\n" +
							"服务器在线状态:" + (upState ? "是" : "否"));
					break;
				case "help":
				default:
					System.out.println("== 列表插件帮助 == \n" +
							"publish start 启动列表公开\n" +
							"publish stop 关闭列表公开\n" +
							"publish motd <内容> 设置服务器motd\n" +
							"publish state 查看公开状态\n" +
							"publish help 查看此帮助");
			}
		}
	}
	
	private String base64ToString(String base64) {
		return new String(Base64.decodeFast(base64));
	}
	
	private void addServer() {
		FormBody.Builder formBody = new FormBody.Builder();
        formBody.add("Version", "HPS#1");
		OkHttpClient client = new OkHttpClient();
		Request req = new Request.Builder().
						url("https://api.data.der.kim/UpList/v5/upList-RT").
						post(formBody.build()).
						build();
		Call call = client.newCall(req);
		call.enqueue(new Callback() {
				@Override
				public void onFailure(Call p1, IOException p2) {
					log.warn("Network error!");
				}

				@Override
				public void onResponse(Call p1, Response rep) throws IOException {
					String result = rep.body().string();
					if (result.startsWith("[-1]")) {
						log.warn("API Error:" + result);
					} else if (result.startsWith("[-2]")) {
						log.warn("You are in the blacklist:" + result);
					} else {
						JSONObject obj = JSON.parseObject(result);
						sid = base64ToString(obj.getString("id"));
						addData = base64ToString(obj.getString("add"));
						selfCheckData = base64ToString(obj.getString("open"));
						updateData = base64ToString(obj.getString("update"));
						removeData = base64ToString(obj.getString("remove"));
						/*conn.sendServerMessage(String.format("Result: sid=%s, add=%s, open=%s, update=%s, remove=%s",
															sid, addData, selfCheckData, updateData, removeData));*/


						String targetData = addData.replace("{RW-HPS.S.NAME}", Rukkit.getConfig().serverUser).
													replace("{RW-HPS.S.PRIVATE.IP}", "10.0.0.1").
													replace("{RW-HPS.S.PORT}", String.valueOf(Rukkit.getConfig().serverPort)).
													replace("{RW-HPS.RW.MAP.NAME}", Rukkit.getConfig().serverMotd).
													replace("{RW-HPS.PLAYER.SIZE}", String.valueOf(Rukkit.getGlobalConnectionManager().size())).
													replace("{RW-HPS.PLAYER.SIZE.MAX}", String.valueOf(Rukkit.getConfig().maxPlayer * Rukkit.getConfig().maxRoom)).
													replace("{RW-HPS.RW.VERSION}", "1.15").
													replace("{RW-HPS.RW.VERSION.INT}", "176").
													replace("{RW-HPS.RW.IS.VERSION}", "false").
													replace("{RW-HPS.RW.IS.PASSWD}", "false");
						Callback back = new Callback() {

							@Override
							public void onFailure(Call p1, IOException p2) {
								log.warn("IO Exception:", p2);
							}

							@Override
							public void onResponse(Call p1, Response rep) throws IOException {
								String result = rep.body().string();
								if (result.contains(sid)) {
									upState = true;
									log.info("Server was pubished.");
									log.debug("OK!");
									updateFuture = Rukkit.getThreadManager().schedule(new UpdateTask(), 1000*15, 1000*15);
									isPublished = true;
								} else {
									upState = false;
									log.warn("Server publish failed.");
									log.debug("FAIL!");
								}
							}
						};
						post("http://gs1.corrodinggames.com/masterserver/1.4/interface", targetData, back);
						post("http://gs4.corrodinggames.net/masterserver/1.4/interface", targetData, back);

						String check = selfCheckData.replace("{RW-HPS.S.PORT}", String.valueOf(Rukkit.getConfig().serverPort));

						Callback backCheck = new Callback() {

							@Override
							public void onFailure(Call p1, IOException p2) {
								log.warn("IO Exception:", p2);
							}

							@Override
							public void onResponse(Call p1, Response rep) throws IOException {
								String result = rep.body().string();
								if (result.contains("true")) {
									log.info("Server was Port pubished.");
								} else {
									log.info("Server was Port not pubished.");
								}
							}
						};
						post("http://gs1.corrodinggames.com/masterserver/1.4/interface", check, backCheck);
						post("http://gs4.corrodinggames.net/masterserver/1.4/interface", check, backCheck);
					}
				}
			});
			
	}
	
	private FormBody requestToFormBody(String str) {
		FormBody.Builder formBody = new FormBody.Builder();
		final String[] arr = str.split("&");
		for (String pam : arr) {
			final String[] kv = pam.split("=");
			formBody.add(kv[0],kv[1]);
		}
		return formBody.build();
	}
	
	private void post(String url, String data, Callback back) {
		Request req = new Request.Builder().
			url(url).
			post(requestToFormBody(data)).
			build();
		OkHttpClient client = new OkHttpClient();
		Call call = client.newCall(req);
		call.enqueue(back);
	}
	
	private void post(String url, String data) {
		post(url, data, new Callback() {

				@Override
				public void onFailure(Call p1, IOException p2) {
				}

				@Override
				public void onResponse(Call p1, Response p2) throws IOException {
					log.debug("Success: {}", p2.body().string());
				}
			});
	}
	
	private void updateServer() {
		String targetData = updateData.replace("{RW-HPS.S.NAME}", Rukkit.getConfig().serverUser).
										replace("{RW-HPS.S.PRIVATE.IP}", "10.0.0.1").
										replace("{RW-HPS.S.PORT}", String.valueOf(Rukkit.getConfig().serverPort)).
										replace("{RW-HPS.RW.MAP.NAME}", Rukkit.getConfig().serverMotd).
										replace("{RW-HPS.PLAYER.SIZE}", String.valueOf(Rukkit.getGlobalConnectionManager().size())).
										replace("{RW-HPS.PLAYER.SIZE.MAX}", String.valueOf(Rukkit.getConfig().maxPlayer * Rukkit.getConfig().maxRoom)).
										replace("{RW-HPS.RW.IS.PASSWD}", "false");

		targetData = targetData.replace("{RW-HPS.S.STATUS}", Rukkit.getRoomManager().getAvailableRoom() == null ? "ingame": "battleroom");
		// 已弃用
//		if (!Rukkit.getConfig().nonStopMode) {
//
//		} else {
//			targetData = targetData.replace("{RW-HPS.S.STATUS}", "battleroom");
//		}
		Callback back = new Callback() {

			@Override
			public void onFailure(Call p1, IOException p2) {
				log.warn("Updating Server:: IO Exception:", p2);
			}

			@Override
			public void onResponse(Call p1, Response rep) throws IOException {
				String result = rep.body().string();
				if (result.contains("UPDATED")) {
					upState = true;
					log.debug("OK!");
				} else {
					upState = false;
					log.warn("Warning: Updating server error: {}", result);
					log.debug("FAIL!");
				}
			}
		};
		post("http://gs1.corrodinggames.com/masterserver/1.4/interface", targetData, back);
		post("http://gs4.corrodinggames.net/masterserver/1.4/interface", targetData, back);
	}
	
	private void stopServer() {
		post("http://gs1.corrodinggames.com/masterserver/1.4/interface", removeData);
		post("http://gs4.corrodinggames.net/masterserver/1.4/interface", removeData);
		Rukkit.getThreadManager().shutdownTask(updateFuture);
		isPublished = false;
		upState = false;
	}
	
	@Override
	public void onLoad() {
		log = getLogger();
		log.info("UplistPlugin:init");
		log.info("Server MOTD: {}", Rukkit.getConfig().serverMotd);
		log.info("Server UUID: {}", Rukkit.getConfig().UUID);
		log.info("Server User: {}", Rukkit.getConfig().serverUser);
		Rukkit.getCommandManager().registerServerCommand(new ServerCommand("publish", "列表公开插件命令", 2, new PublishServerCommandListener(), this));
	}
	
	@Override
	public void onEnable() {
		
	}
	
	@Override
	public void onDisable() {
		
	}
	
	@Override
	public void onStart() {
		
	}
	
	@Override
	public void onDone() {
		
	}
}
