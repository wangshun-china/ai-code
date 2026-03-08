

// import com.yupi.yuaicodemother.ai.AiCodeGenTypeRoutingService;
// import com.yupi.yuaicodemother.ai.AiCodeGeneratorService;

// import com.yupi.yuaicodemother.ai.model.HtmlCodeResult;
// import com.yupi.yuaicodemother.ai.tools.FileWriteTool;
// import com.yupi.yuaicodemother.constant.AppConstant;
// import com.yupi.yuaicodemother.model.enums.CodeGenTypeEnum;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import reactor.core.publisher.Flux;

// import java.nio.file.Files;
// import java.nio.file.Paths;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.TimeUnit;

// import static org.junit.jupiter.api.Assertions.*;

// /**
//  * 核心功能简单测试（无需启动服务，直接测试核心服务/工具）
//  */
// @SpringBootTest // 加载Spring上下文，自动注入依赖
// public class AiCodeGenSimpleTest {

//     // 注入核心服务和工具
//     @Autowired
//     private AiCodeGenTypeRoutingService routingService;
//     @Autowired
//     private AiCodeGeneratorService codeGeneratorService;
//     @Autowired
//     private FileWriteTool fileWriteTool;

//     /**
//      * 测试1：路由服务（根据需求自动判断生成类型）
//      */
//     @Test
//     void testRouteCodeGenType() {
//         // 简单需求（预期返回 HTML 类型）
//         String simplePrompt = "生成一个个人简历静态页面，只有基本信息展示";
//         CodeGenTypeEnum simpleType = routingService.routeCodeGenType(simplePrompt);
//         assertEquals(CodeGenTypeEnum.HTML, simpleType, "简单需求应路由到 HTML 类型");

//         // 复杂需求（预期返回 VUE_PROJECT 类型）
//         String complexPrompt = "生成电商平台，支持商品列表、购物车、订单管理、用户登录，多页面切换";
//         CodeGenTypeEnum complexType = routingService.routeCodeGenType(complexPrompt);
//         assertEquals(CodeGenTypeEnum.VUE_PROJECT, complexType, "复杂需求应路由到 VUE_PROJECT 类型");

//         System.out.println("测试1：路由服务测试通过 ✅");
//     }

//     /**
//      * 测试2：HTML 代码生成（验证生成结果有效性）
//      */
//     @Test
//     void testGenerateHtmlCode() {
//         String userPrompt = "生成一个响应式的个人博客首页，有导航栏、文章列表、侧边栏";
//         HtmlCodeResult result = codeGeneratorService.generateHtmlCode(userPrompt);

//         // 断言结果不为空
//         assertNotNull(result, "生成结果不能为 null");
//         assertNotNull(result.getHtmlCode(), "HTML 代码不能为 null");
//         assertFalse(result.getHtmlCode().trim().isEmpty(), "HTML 代码不能为空");
//         // 断言包含核心标签（响应式布局相关）
//         assertTrue(result.getHtmlCode().contains("<style>"), "HTML 应包含内联 CSS");
//         assertTrue(result.getHtmlCode().contains("<script>"), "HTML 应包含内联 JS");
//         assertTrue(result.getHtmlCode().contains("flex") || result.getHtmlCode().contains("grid"), "应使用 Flex/Grid 布局");

//         System.out.println("测试2：HTML 生成测试通过 ✅");
//         System.out.println("生成的 HTML 代码长度：" + result.getHtmlCode().length() + " 字符");
//     }

//     /**
//      * 测试3：工具调用（文件写入工具）
//      */
//     @Test
//     void testFileWriteTool() {
//         String testFilePath = "test-blog.html";
//         String testContent = "<h1>测试文件</h1>";
//         Long testAppId = 1001L;

//         // 调用文件写入工具
//         String result = fileWriteTool.writeFile(testFilePath, testContent, testAppId);

//         // 断言写入成功
//         assertTrue(result.contains("文件写入成功"), "文件写入应返回成功信息");

//         // 验证文件实际存在（拼接项目目录路径）
//         String projectDir = "vue_project_" + testAppId;
//         String fullPath = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDir, testFilePath).toString();
//         assertTrue(Files.exists(Paths.get(fullPath)), "文件应实际写入到指定目录");

//         System.out.println("测试3：文件写入工具测试通过 ✅");
//         System.out.println("文件实际路径：" + fullPath);
//     }

//     /**
//      * 测试4：Vue 项目流式生成（验证流式响应）
//      * 注意：流式响应是异步的，需要用 CountDownLatch 等待结束
//      */
//     @Test
//     void testGenerateVueProjectStream() throws InterruptedException {
//         Long testAppId = 1002L;
//         String userPrompt = "生成一个简单的 Vue3 待办清单项目，支持添加、删除待办项";
//         CountDownLatch latch = new CountDownLatch(1); // 等待流式响应结束

//         // 调用流式生成服务
//         Flux<String> stream = codeGeneratorService.generateVueProjectCodeStream(testAppId, userPrompt);

//         // 订阅流式响应，验证是否有内容返回
//         stream.subscribe(
//                 chunk -> {
//                     // 断言每个流片段不为空
//                     assertFalse(chunk.trim().isEmpty(), "流式响应片段不能为空");
//                     System.out.println("流式响应片段：" + chunk.substring(0, Math.min(50, chunk.length())) + "...");
//                 },
//                 error -> {
//                     fail("流式生成出现异常：" + error.getMessage());
//                     latch.countDown();
//                 },
//                 latch::countDown // 响应结束，计数器减1
//         );

//         // 等待流式响应完成（最多等待30秒）
//         boolean completed = latch.await(30, TimeUnit.SECONDS);
//         assertTrue(completed, "流式生成应在30秒内完成");

//         System.out.println("测试4：Vue 流式生成测试通过 ✅");
//     }
// }