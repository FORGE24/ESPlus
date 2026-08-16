import { PageContainer, Card } from '../components/ui/Card'
import { Badge } from '../components/ui/DataTable'

interface RoleInfo {
  role: string
  label: string
  badgeType: 'ok' | 'warn' | 'info'
  color: string
  description: string
  permissions: { name: string; desc: string }[]
}

const ROLES: RoleInfo[] = [
  {
    role: 'ADMIN',
    label: '管理员',
    badgeType: 'danger' as 'ok',
    color: 'var(--accent-rose)',
    description: '最高权限角色，可执行所有面板操作，包括系统管理、安全配置和用户管理。',
    permissions: [
      { name: '系统管理', desc: '停服、重载、保存、维护模式、任务调度' },
      { name: '安全配置', desc: 'TOTP 管理、紧急严打、配置回滚、安全快照' },
      { name: '用户管理', desc: '设置管理员、角色分配、OP 绑定、解锁账户' },
      { name: '操作审批', desc: '审批或拒绝高危操作请求' },
      { name: '游戏控制台', desc: '执行任意服务器命令' },
      { name: '数据保留', desc: '配置数据保留策略、执行清理' },
      { name: '全量只读', desc: '查看所有面板数据、审计日志、告警' },
    ],
  },
  {
    role: 'MODERATOR',
    label: '版主',
    badgeType: 'warn',
    color: 'var(--accent-amber)',
    description: '日常管理角色，可执行玩家管理、消息通讯和基础世界操作，但不能修改系统配置。',
    permissions: [
      { name: '玩家管理', desc: '踢出、封禁、解封、切换游戏模式' },
      { name: '消息通讯', desc: '广播、私信、标题字幕、定时广播' },
      { name: '世界操作', desc: '修改时间、天气、难度、出生点' },
      { name: '物品操作', desc: '给予物品、清空背包、查看末影箱' },
      { name: '实体管理', desc: '实体统计、一键清理' },
      { name: '告警处理', desc: '查看并确认异常告警' },
      { name: '审计查看', desc: '查看安全审计日志（只读）' },
    ],
  },
  {
    role: 'VIEWER',
    label: '只读',
    badgeType: 'info',
    color: 'var(--accent-cyan)',
    description: '只读角色，可查看所有面板数据和统计信息，但不能执行任何修改操作。',
    permissions: [
      { name: '仪表盘', desc: '查看服务总览、TPS、在线玩家' },
      { name: '玩家列表', desc: '查看在线玩家、玩家档案' },
      { name: '状态监控', desc: '查看性能数据、长期趋势、连接信息' },
      { name: '审计日志', desc: '查看安全审计记录（只读）' },
      { name: '告警查看', desc: '查看异常告警（不可确认）' },
      { name: '物品溯源', desc: '查看物品追踪链和事件搜索' },
      { name: '安全分析', desc: '查看风险评分、经济审计、Mod 来源' },
    ],
  },
]

export default function SecurityAccounts() {
  return (
    <PageContainer title="面板账号" subtitle="三种账户角色及其权限说明">
      <div style={{ display: 'grid', gap: 20 }}>
        {ROLES.map((r) => (
          <Card key={r.role} title={`${r.label}（${r.role}）`} actions={<Badge type={r.badgeType as 'ok'}>{r.role}</Badge>}>
            <p style={{ fontSize: 14, color: 'var(--ink-300)', marginBottom: 16, lineHeight: 1.6 }}>
              {r.description}
            </p>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
              gap: 12,
            }}>
              {r.permissions.map((p) => (
                <div
                  key={p.name}
                  style={{
                    padding: '12px 14px',
                    background: 'var(--bg-700)',
                    border: '1px solid var(--glass-border)',
                    borderLeft: `3px solid ${r.color}`,
                    borderRadius: 'var(--radius-sm)',
                  }}
                >
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-100)', marginBottom: 4 }}>
                    {p.name}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--ink-400)' }}>
                    {p.desc}
                  </div>
                </div>
              ))}
            </div>
          </Card>
        ))}
      </div>

      <Card title="角色对比总结" style={{ marginTop: 20 }}>
        <div className="data-table" style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>能力</th>
                <th>ADMIN</th>
                <th>MODERATOR</th>
                <th>VIEWER</th>
              </tr>
            </thead>
            <tbody>
              {[
                { cap: '查看面板数据', a: true, m: true, v: true },
                { cap: '玩家管理操作', a: true, m: true, v: false },
                { cap: '消息与广播', a: true, m: true, v: false },
                { cap: '世界与游戏规则', a: true, m: true, v: false },
                { cap: '系统管理（停服/重载）', a: true, m: false, v: false },
                { cap: '安全配置（TOTP/严打）', a: true, m: false, v: false },
                { cap: '用户与角色管理', a: true, m: false, v: false },
                { cap: '操作审批', a: true, m: false, v: false },
                { cap: '游戏控制台', a: true, m: false, v: false },
              ].map((row) => (
                <tr key={row.cap}>
                  <td>{row.cap}</td>
                  <td>{row.a ? <Badge type="ok">是</Badge> : <Badge type="danger">否</Badge>}</td>
                  <td>{row.m ? <Badge type="ok">是</Badge> : <Badge type="danger">否</Badge>}</td>
                  <td>{row.v ? <Badge type="ok">是</Badge> : <Badge type="danger">否</Badge>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </PageContainer>
  )
}
