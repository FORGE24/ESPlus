// ═══════════════════════════════════════════════════════════
// ES+ Panel — Navigation Configuration
// ═══════════════════════════════════════════════════════════

export interface NavItem {
  path: string
  label: string
  icon: string
  admin?: boolean
}
export interface NavSection {
  title: string
  items: NavItem[]
}

export const NAV: NavSection[] = [
  {
    title: '状态总览',
    items: [
      { path: '/', label: '仪表盘', icon: 'grid' },
      { path: '/status', label: '服务总览', icon: 'server' },
      { path: '/status/performance', label: '性能监视', icon: 'activity' },
      { path: '/status/trends', label: '长期趋势', icon: 'trending' },
      { path: '/status/connection', label: '连接与端口', icon: 'wifi' },
      { path: '/status/versions', label: '模组/版本', icon: 'box' },
    ],
  },
  {
    title: '玩家与会话',
    items: [
      { path: '/players', label: '在线列表', icon: 'users' },
      { path: '/players/profile', label: '玩家档案', icon: 'user' },
      { path: '/bans', label: '封禁名单', icon: 'ban' },
      { path: '/whitelist', label: '白名单', icon: 'check' },
      { path: '/players/actions', label: '操场历史', icon: 'clock' },
    ],
  },
  {
    title: '通讯与公告',
    items: [
      { path: '/messages', label: '广播 / 私信', icon: 'message' },
      { path: '/messages/schedule', label: '定时广播', icon: 'calendar' },
      { path: '/messages/title', label: '标题字幕', icon: 'type' },
      { path: '/messages/bossbar', label: 'Boss 血条', icon: 'bar' },
      { path: '/messages/filter', label: '聊天过滤', icon: 'filter' },
      { path: '/messages/mute', label: '禁言管理', icon: 'mute' },
    ],
  },
  {
    title: '世界与环境',
    items: [
      { path: '/world/time', label: '时间天气', icon: 'sun' },
      { path: '/world/difficulty', label: '难度与模式', icon: 'sword' },
      { path: '/world/border', label: '世界边界', icon: 'frame' },
      { path: '/world/spawn', label: '出生点', icon: 'home' },
      { path: '/world/dimensions', label: '维度总览', icon: 'globe' },
    ],
  },
  {
    title: '游戏规则',
    items: [
      { path: '/gamerules', label: 'Gamerule 矩阵', icon: 'sliders' },
    ],
  },
  {
    title: '实体与清场',
    items: [
      { path: '/entities', label: '实体统计', icon: 'box' },
      { path: '/entities/cleanup', label: '一键清理', icon: 'trash' },
    ],
  },
  {
    title: '物品与背包',
    items: [
      { path: '/items/give', label: '给予物品', icon: 'gift' },
      { path: '/items/inventory', label: '背包/末影箱', icon: 'backpack' },
      { path: '/items/clear', label: '清空背包', icon: 'eraser' },
      { path: '/search', label: '物品溯源', icon: 'search' },
    ],
  },
  {
    title: '权限与安全',
    items: [
      { path: '/admins', label: '设置管理员', icon: 'shield', admin: true },
      { path: '/security/sudo', label: 'sudo 策略', icon: 'key' },
      { path: '/security/accounts', label: '面板账号', icon: 'id' },
      { path: '/audit', label: '安全审计', icon: 'scroll' },
      { path: '/alerts', label: '异常告警', icon: 'alert' },
    ],
  },
  {
    title: '安全分析',
    items: [
      { path: '/security/risk', label: '风险评分', icon: 'gauge' },
      { path: '/security/approvals', label: '操作审批', icon: 'check-decided', admin: true },
      { path: '/security/integrity', label: '审计完整性', icon: 'fingerprint' },
      { path: '/security/webhooks', label: '告警通道', icon: 'webhook' },
      { path: '/security/config-history', label: '配置历史', icon: 'history', admin: true },
      { path: '/security/snapshots', label: '安全快照', icon: 'camera', admin: true },
      { path: '/security/economy', label: '经济审计', icon: 'coins' },
      { path: '/security/mod-sources', label: 'Mod 来源', icon: 'package' },
      { path: '/security/mfa', label: '面板 TOTP', icon: 'qr', admin: true },
      { path: '/security/lockdown', label: '紧急严打', icon: 'lock', admin: true },
      { path: '/center', label: '本机 Center', icon: 'crosshair' },
    ],
  },
  {
    title: '名单与访问',
    items: [
      { path: '/access/ops', label: 'OP 列表', icon: 'crown' },
      { path: '/access/spectator', label: '旁观者策略', icon: 'eye' },
    ],
  },
  {
    title: '记分板 / 队伍',
    items: [
      { path: '/scoreboard', label: '记分板目标', icon: 'target' },
      { path: '/scoreboard/teams', label: '队伍管理', icon: 'flag' },
    ],
  },
  {
    title: '系统维护',
    items: [
      { path: '/console', label: '游戏控制台', icon: 'terminal', admin: true },
      { path: '/remote', label: '远程运维 / SSH', icon: 'ssh' },
      { path: '/system/save', label: '保存与流畅', icon: 'save', admin: true },
      { path: '/system/retention', label: '数据保留', icon: 'archive', admin: true },
      { path: '/system/reload', label: '重载', icon: 'refresh', admin: true },
      { path: '/system/stop', label: '停服', icon: 'power', admin: true },
      { path: '/system/maintenance', label: '维护模式', icon: 'wrench', admin: true },
      { path: '/system/schedules', label: '任务调度', icon: 'schedule', admin: true },
      { path: '/automation', label: '⚡ 自动化', icon: 'bolt' },
      { path: '/system/runtime', label: '运行时配置', icon: 'settings' },
    ],
  },
  {
    title: '诊断与日志',
    items: [
      { path: '/diag/logs', label: '服务器日志', icon: 'file-text' },
      { path: '/diag/actions', label: '面板动作', icon: 'list' },
      { path: '/diag/movements', label: '移动轨迹', icon: 'route' },
    ],
  },
]

// Flatten for quick lookup
export const ALL_PATHS = NAV.flatMap((s) => s.items.map((i) => i.path))
