const express = require('express');
const { exec } = require('child_process');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

// 中间件
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// 代码输出根目录（与Java服务共享）
const CODE_OUTPUT_ROOT = '/tmp/code_output';

// 健康检查端点
app.get('/health', (req, res) => {
  res.json({ status: 'healthy', timestamp: new Date().toISOString() });
});

// 构建端点
app.post('/build', async (req, res) => {
  const { projectDirName } = req.body;

  if (!projectDirName) {
    return res.status(400).json({
      success: false,
      message: '缺少参数: projectDirName'
    });
  }

  const projectPath = path.join(CODE_OUTPUT_ROOT, projectDirName);

  // 检查项目目录是否存在
  if (!fs.existsSync(projectPath)) {
    return res.status(404).json({
      success: false,
      message: `项目目录不存在: ${projectPath}`
    });
  }

  // 检查package.json是否存在
  const packageJsonPath = path.join(projectPath, 'package.json');
  if (!fs.existsSync(packageJsonPath)) {
    return res.status(400).json({
      success: false,
      message: 'package.json 不存在，不是有效的Vue项目'
    });
  }

  console.log(`开始构建项目: ${projectDirName}`);

  try {
    // 执行构建命令
    const result = await execBuild(projectPath);

    if (result.success) {
      console.log(`项目构建成功: ${projectDirName}`);
      res.json({
        success: true,
        message: 'Build Success',
        projectDirName
      });
    } else {
      console.error(`项目构建失败: ${projectDirName}`, result.error);
      res.status(500).json({
        success: false,
        message: `构建失败: ${result.error}`
      });
    }
  } catch (error) {
    console.error(`构建过程异常: ${projectDirName}`, error);
    res.status(500).json({
      success: false,
      message: `构建异常: ${error.message}`
    });
  }
});

// 执行构建命令
// 修改后的 execBuild 函数
function execBuild(projectPath) {
  return new Promise((resolve) => {
    // 强制清理 node_modules 和 lock 文件
    // 防止 Windows/Linux 环境冲突，或者之前的缓存导致 vite 没装上
    const commands = [
      `cd "${projectPath}"`,
      'rm -rf node_modules package-lock.json', // 🔥 第一步：核弹级清理（关键！）
      'npm install --include=dev --registry=https://registry.npmmirror.com --no-audit --no-fund', // 🔥 第二步：指定淘宝源重新装
      'npm run build' // 🔥 第三步：构建
    ].join(' && ');

    console.log(`[${path.basename(projectPath)}] 执行命令: ${commands}`);

    const child = exec(commands, {
      cwd: projectPath,
      maxBuffer: 20 * 1024 * 1024 // 加大 buffer 到 20MB，防止日志截断
    });

    let stdout = '';
    let stderr = '';

    child.stdout.on('data', (data) => {
      stdout += data.toString();
      // 这里的日志可能会非常多，建议开发调试时开启，生产环境可以视情况注释
      // console.log(`[${path.basename(projectPath)}] ${data}`);
    });

    child.stderr.on('data', (data) => {
      stderr += data.toString();
      console.error(`[${path.basename(projectPath)}] ${data}`);
    });

    child.on('close', (code) => {
      if (code === 0) {
        resolve({ success: true, stdout, stderr });
      } else {
        // 如果失败，返回详细的 stderr
        resolve({
          success: false,
          error: stderr || `构建进程退出码: ${code}`,
          stdout,
          stderr
        });
      }
    });

    child.on('error', (error) => {
      resolve({ success: false, error: error.message });
    });

    // 设置超时（延长到 15 分钟，因为要重新下载依赖）
    setTimeout(() => {
      child.kill();
      resolve({ success: false, error: '构建超时（15分钟）' });
    }, 15 * 60 * 1000);
  });
}

// 启动服务器
app.listen(PORT, () => {
  console.log(`Node构建器服务运行在端口 ${PORT}`);
  console.log(`代码输出根目录: ${CODE_OUTPUT_ROOT}`);
});