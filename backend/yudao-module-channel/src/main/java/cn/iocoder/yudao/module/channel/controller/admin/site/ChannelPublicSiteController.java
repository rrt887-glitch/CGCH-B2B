package cn.iocoder.yudao.module.channel.controller.admin.site;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelSiteProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

/**
 * 对外公开页面：产品介绍、隐私政策、服务条款、数据删除说明。
 * Meta / 微软 / Google 审核授权应用时要求提供这些页面的公开地址；Nginx 把 /about、/privacy、/terms、/data-deletion
 * 转到这里（见 deploy/nginx.conf）。页面是服务端直接输出的 HTML，审核方的抓取工具不执行脚本也能看到全文。
 */
@Tag(name = "公开页面 - 产品介绍与隐私政策")
@RestController
@RequestMapping("/public/site")
public class ChannelPublicSiteController {

    @Resource
    private ChannelSiteProperties site;

    @GetMapping(value = "/{page}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @Operation(summary = "公开页面：about、privacy、terms、data-deletion")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> page(@PathVariable("page") String page) {
        String[] tpl = switch (page) {
            case "about" -> new String[]{"About", ABOUT};
            case "privacy" -> new String[]{"Privacy Policy 隐私政策", PRIVACY};
            case "terms" -> new String[]{"Terms of Service 服务条款", TERMS};
            case "data-deletion" -> new String[]{"User Data Deletion 数据删除", DELETION};
            default -> null;
        };
        if (tpl == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");
        }
        return ResponseEntity.ok(render(tpl[0], tpl[1]));
    }

    String render(String title, String body) {
        String company = StrUtil.blankToDefault(site.getCompanyName(), "（请配置 CGCH_COMPANY_NAME）");
        String companyEn = StrUtil.blankToDefault(site.getCompanyNameEn(), company);
        String email = StrUtil.blankToDefault(site.getContactEmail(), "（请配置 CGCH_CONTACT_EMAIL）");
        Map<String, String> vars = Map.of(
                "{product}", HtmlUtils.htmlEscape(site.getProductName()),
                "{company}", HtmlUtils.htmlEscape(company),
                "{companyEn}", HtmlUtils.htmlEscape(companyEn),
                "{email}", HtmlUtils.htmlEscape(email),
                "{address}", HtmlUtils.htmlEscape(StrUtil.blankToDefault(site.getAddress(), "—")),
                "{date}", HtmlUtils.htmlEscape(site.getEffectiveDate()));
        String html = LAYOUT.replace("{title}", HtmlUtils.htmlEscape(title)).replace("{body}", body);
        for (Map.Entry<String, String> e : vars.entrySet()) {
            html = html.replace(e.getKey(), e.getValue());
        }
        return html;
    }

    private static final String LAYOUT = """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>{title} · {product}</title>
            <style>
            :root{color-scheme:light dark;--fg:#1f2328;--muted:#59636e;--line:#d1d9e0;--bg:#fff;--accent:#0b57d0}
            @media (prefers-color-scheme:dark){:root{--fg:#e6edf3;--muted:#9198a1;--line:#3d444d;--bg:#0d1117;--accent:#8ab4f8}}
            body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.7 system-ui,-apple-system,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif}
            main{max-width:820px;margin:0 auto;padding:32px 16px 64px}
            nav{display:flex;flex-wrap:wrap;gap:16px;border-bottom:1px solid var(--line);padding-bottom:12px;margin-bottom:24px}
            nav a{color:var(--accent);text-decoration:none}
            h1{font-size:28px;margin:0 0 4px}h2{font-size:20px;margin:32px 0 8px}h3{font-size:17px;margin:20px 0 4px}
            .muted{color:var(--muted);font-size:14px}hr{border:0;border-top:1px solid var(--line);margin:40px 0}
            li{margin:4px 0}
            </style></head><body><main>
            <nav><strong>{product}</strong><a href="/about">About</a><a href="/privacy">Privacy Policy</a><a href="/terms">Terms of Service</a><a href="/data-deletion">Data Deletion</a><a href="/">Sign in</a></nav>
            {body}
            <hr><p class="muted">{companyEn} · {address} · <a href="mailto:{email}">{email}</a></p>
            </main></body></html>
            """;

    private static final String ABOUT = """
            <h1>{product}</h1>
            <p class="muted">Operated by {companyEn}</p>
            <p>{product} is a customer-communication workspace for export and B2B sales teams. A company signs up, and its sales
            staff connect the business accounts they already own — company mailboxes, WhatsApp Business numbers, Facebook Pages
            and Instagram professional accounts — so that customer inquiries from every channel arrive in one inbox.</p>
            <h2>What the product does</h2>
            <ul>
            <li><b>Unified inbox.</b> Receive and reply to customer emails, WhatsApp messages, Facebook Page (Messenger) messages and Instagram Direct messages in one place.</li>
            <li><b>AI reply drafts.</b> An AI assistant drafts replies from the company's own product knowledge base. Staff review drafts, or let the assistant answer routine questions automatically within limits the company sets.</li>
            <li><b>Lead management.</b> New inquiries become sales leads in the built-in CRM and are assigned to the right salesperson.</li>
            </ul>
            <h2>How accounts are connected</h2>
            <p>Each user explicitly connects their own accounts through the platform's official authorization screen (for example
            "Continue with Facebook" or "Sign in with Microsoft"). The product only accesses messages of accounts the user has
            connected, only to show them in the inbox and to send the replies the user or the company's assistant writes. Users can
            disconnect an account at any time in <i>Configuration → Channels</i>.</p>
            <p>See our <a href="/privacy">Privacy Policy</a> for details. Contact: <a href="mailto:{email}">{email}</a></p>
            <hr>
            <h1>{product} 简介</h1>
            <p>{product} 是面向外贸与 B2B 销售团队的客户沟通工作台，由 {company} 运营。企业注册后，业务员可以接入自己的企业邮箱、WhatsApp Business 号码、
            Facebook 主页和 Instagram 专业账号，各渠道的客户询盘集中在一个收件箱里处理；AI 助手根据企业的产品知识起草回复，新询盘自动建为销售线索。
            所有账号都由用户本人通过平台官方授权页面主动接入，可以随时解除。</p>
            """;

    private static final String PRIVACY = """
            <h1>Privacy Policy</h1>
            <p class="muted">Effective date: {date} · {companyEn} ("we", "us")</p>
            <p>This policy explains what information {product} collects, how we use it, and the choices you have. {product} is a
            business service: our customers are companies ("Customers"), and their staff ("Users") use it to communicate with the
            Customer's own clients ("Contacts").</p>

            <h2>1. Information we collect</h2>
            <h3>Account information</h3>
            <p>Name, username, email address, phone number and company details that a Customer or User provides when registering.</p>
            <h3>Data from accounts you connect</h3>
            <p>When a User connects a third-party account, we receive only what is needed to provide the inbox:</p>
            <ul>
            <li><b>Email accounts</b> (IMAP/SMTP, Microsoft Outlook / Microsoft 365): the mailbox address, OAuth tokens or the app password you enter, and the email messages and attachments in the mailbox.</li>
            <li><b>Facebook Pages and Instagram professional accounts</b> (Meta Platform): the Page or account ID and name, a Page access token, and the messages exchanged between the Page or account and people who message it, including their name or username as provided by Meta, and attachments.</li>
            <li><b>WhatsApp Business</b> (WhatsApp Business Platform): the phone number ID, an access token, and messages exchanged with customers, including their WhatsApp profile name and phone number.</li>
            </ul>
            <h3>Usage and technical data</h3>
            <p>Log data such as IP address, browser type, pages visited and timestamps, used for security and troubleshooting.</p>

            <h2>2. How we use information</h2>
            <ul>
            <li>To display, send and organize messages in the User's inbox, and to create sales leads from inquiries.</li>
            <li>To generate reply drafts: when the Customer enables the AI assistant, the text of the relevant conversation and the Customer's knowledge base are sent to the large language model provider that the Customer configures, solely to generate the draft.</li>
            <li>To notify Users of new inquiries, to provide support, to secure the service and to comply with law.</li>
            </ul>
            <p>We do <b>not</b> sell personal information, use it for advertising, or use data obtained from Meta, Microsoft or Google
            to build profiles for any purpose other than providing the features described above. We do not use such data to train
            general-purpose AI models.</p>

            <h2>3. Platform-specific commitments</h2>
            <ul>
            <li><b>Meta (Facebook, Instagram, WhatsApp):</b> we use Platform Data only to provide the messaging features the User has enabled, in accordance with the Meta Platform Terms and Developer Policies. Users can remove our app at any time in their Facebook settings (<i>Settings → Business integrations</i>), and request deletion as described in our <a href="/data-deletion">Data Deletion</a> page.</li>
            <li><b>Microsoft:</b> mailbox access is used only to read and send mail for the connected mailbox on the User's behalf.</li>
            <li><b>Google:</b> if Google account access is offered, our use and transfer of information received from Google APIs will adhere to the <a href="https://developers.google.com/terms/api-services-user-data-policy">Google API Services User Data Policy</a>, including the Limited Use requirements.</li>
            </ul>

            <h2>4. Sharing</h2>
            <p>We share information only with: (a) the Customer the User belongs to; (b) service providers that host or operate the
            service for us (cloud hosting, email delivery), under confidentiality obligations; (c) the AI model provider chosen by
            the Customer, as described above; (d) the messaging platforms themselves when you send a message; and (e) authorities
            when required by law.</p>

            <h2>5. Security</h2>
            <p>Data is transmitted over HTTPS. Access tokens, passwords and API keys are stored encrypted (AES). Access inside a
            Customer's workspace is restricted by role and data permissions, and each Customer's data is isolated from other Customers.</p>

            <h2>6. Retention and deletion</h2>
            <p>We keep data while the account is connected and the Customer's subscription is active. When a User disconnects an
            account, its access token is deleted and messages synchronized from it are deleted. A Customer can ask us to delete its
            whole workspace; we delete it within 30 days, except where law requires us to keep records. See <a href="/data-deletion">Data Deletion</a>.</p>

            <h2>7. Your rights</h2>
            <p>You may request access to, correction of, or deletion of your personal information by contacting us at
            <a href="mailto:{email}">{email}</a>. If you are a Contact of one of our Customers, we may forward your request to that Customer,
            who controls the conversation data.</p>

            <h2>8. International transfers</h2>
            <p>Our servers may be located outside your country. We protect transferred data as described in this policy.</p>

            <h2>9. Children</h2>
            <p>The service is for businesses and is not directed to children under 16.</p>

            <h2>10. Changes and contact</h2>
            <p>We will post changes on this page and update the effective date. Questions: <a href="mailto:{email}">{email}</a>, {companyEn}, {address}.</p>

            <hr>
            <h1>隐私政策</h1>
            <p class="muted">生效日期：{date} · 运营方：{company}</p>
            <p>{product} 是面向企业的客户沟通服务。本政策说明我们收集哪些信息、如何使用，以及您的选择。</p>
            <h2>一、我们收集的信息</h2>
            <ul>
            <li><b>账号信息：</b>注册时填写的姓名、用户名、邮箱、手机号、公司信息。</li>
            <li><b>您主动接入的账号数据：</b>邮箱的地址、授权令牌或授权码、邮件和附件；Facebook 主页 / Instagram 专业账号的编号、名称、主页令牌，以及与客户往来的私信、客户昵称和附件；WhatsApp Business 号码的编号、令牌、与客户往来的消息、客户昵称和号码。</li>
            <li><b>使用与技术数据：</b>IP 地址、浏览器类型、访问时间等，用于安全和排查问题。</li>
            </ul>
            <h2>二、我们如何使用</h2>
            <ul>
            <li>在收件箱中展示、发送和整理消息，并把询盘建为销售线索；</li>
            <li>企业开启 AI 助手后，把相关会话内容和企业知识库发送给企业自行配置的大模型服务商，仅用于生成回复草稿；</li>
            <li>新询盘提醒、客户支持、安全防护和遵守法律。</li>
            </ul>
            <p>我们<b>不会</b>出售个人信息，不会用于广告，不会把从 Meta、微软、Google 获得的数据用于上述功能以外的用途，也不会用这些数据训练通用 AI 模型。</p>
            <h2>三、共享</h2>
            <p>仅在以下情况共享：用户所属企业；为我们提供托管等服务、承担保密义务的服务商；企业选择的大模型服务商；发送消息时的消息平台本身；法律要求时的主管机关。</p>
            <h2>四、安全</h2>
            <p>传输全程 HTTPS；令牌、密码、API Key 加密存储；企业之间数据隔离，企业内部按角色和数据权限控制访问。</p>
            <h2>五、保存与删除</h2>
            <p>账号接入期间保存相关数据。解除接入时删除该账号的令牌和已同步的消息；企业申请注销时，我们在 30 天内删除其全部数据（法律要求保留的除外）。详见<a href="/data-deletion">数据删除说明</a>。</p>
            <h2>六、您的权利与联系方式</h2>
            <p>如需查询、更正或删除个人信息，请联系 <a href="mailto:{email}">{email}</a>。地址：{address}。</p>
            """;

    private static final String TERMS = """
            <h1>Terms of Service</h1>
            <p class="muted">Effective date: {date} · {companyEn}</p>
            <ol>
            <li><b>The service.</b> {product} provides a unified inbox, AI-assisted replies and a CRM for business communication. {companyEn} operates the service.</li>
            <li><b>Accounts.</b> Customers are responsible for their Users and for keeping credentials secure. Users may only connect email, WhatsApp, Facebook and Instagram accounts they own or are authorized to manage on behalf of their company.</li>
            <li><b>Acceptable use.</b> You must comply with applicable law and with the terms and policies of each connected platform, including the Meta Platform Terms, WhatsApp Business Policy and Messenger Platform policies. In particular you must not send spam or unsolicited bulk messages, must honour opt-out requests, and must only message people within the windows and rules the platforms allow.</li>
            <li><b>AI-generated content.</b> AI drafts may be inaccurate. Customers decide whether drafts are sent automatically or reviewed by a person, and remain responsible for messages sent from their accounts.</li>
            <li><b>Customer data.</b> Customers own the data in their workspace. We process it only to provide the service, as described in the <a href="/privacy">Privacy Policy</a>.</li>
            <li><b>Fees.</b> Paid features and credits are charged as shown in the product at the time of purchase.</li>
            <li><b>Suspension and termination.</b> We may suspend accounts that violate these terms or platform policies. Customers may stop using the service at any time and request deletion of their data.</li>
            <li><b>Disclaimer and liability.</b> The service is provided "as is". To the extent permitted by law, our total liability is limited to the fees paid in the 12 months before the claim. Third-party platforms may change or interrupt their services, which may affect features.</li>
            <li><b>Changes and governing law.</b> We may update these terms and will post the new version here. These terms are governed by the laws of the place where {companyEn} is registered.</li>
            <li><b>Contact.</b> <a href="mailto:{email}">{email}</a></li>
            </ol>
            <hr>
            <h1>服务条款</h1>
            <p class="muted">生效日期：{date} · 运营方：{company}</p>
            <ol>
            <li><b>服务内容：</b>{product} 提供多渠道统一收件箱、AI 辅助回复和客户管理。</li>
            <li><b>账号：</b>企业对其员工的使用负责并妥善保管登录信息；只能接入本人拥有或经公司授权管理的邮箱、WhatsApp、Facebook、Instagram 账号。</li>
            <li><b>使用规范：</b>遵守法律及各平台的条款和政策（含 Meta 平台条款、WhatsApp 商业政策、Messenger 平台政策）；不得发送垃圾信息或未经同意的群发，须尊重客户的退订要求，只在平台允许的时间窗口和规则内发送消息。</li>
            <li><b>AI 内容：</b>AI 草稿可能有误，企业自行决定自动发送或人工审核，并对从其账号发出的消息负责。</li>
            <li><b>数据：</b>企业拥有其工作区中的数据，我们仅为提供服务而处理，详见<a href="/privacy">隐私政策</a>。</li>
            <li><b>费用：</b>付费功能和智点按购买时页面所示收费。</li>
            <li><b>暂停与终止：</b>违反本条款或平台政策的账号可能被暂停；企业可随时停止使用并申请删除数据。</li>
            <li><b>免责与责任限制：</b>服务按现状提供；在法律允许范围内，我们的责任以索赔前 12 个月实际支付的费用为限。第三方平台的变更或中断可能影响相关功能。</li>
            <li><b>联系：</b><a href="mailto:{email}">{email}</a></li>
            </ol>
            """;

    private static final String DELETION = """
            <h1>User Data Deletion</h1>
            <p class="muted">{product} · {companyEn}</p>
            <h2>Disconnect an account yourself</h2>
            <ol>
            <li>Sign in to {product} and open <i>Configuration → Channels</i>.</li>
            <li>Click <b>Disconnect</b> next to the email, WhatsApp, Facebook Page or Instagram account.</li>
            <li>The account's access token and all messages synchronized from it are deleted immediately.</li>
            </ol>
            <h2>Remove the app from Facebook</h2>
            <p>In Facebook go to <i>Settings &amp; privacy → Settings → Business integrations</i>, select {product} and click <b>Remove</b>.
            Facebook then notifies us automatically, and we delete the Facebook Pages and Instagram accounts you connected, their tokens
            and their conversations. You will receive a confirmation code that you can check on this site.</p>
            <h2>Request deletion by email</h2>
            <p>Email <a href="mailto:{email}">{email}</a> from the address registered with your account, stating which account or workspace
            to delete. We complete deletion within 30 days and confirm by email.</p>
            <hr>
            <h1>数据删除说明</h1>
            <ol>
            <li><b>自行解除：</b>登录 {product} →「配置中台 → 渠道管理」，点账号旁的「解绑」，该账号的令牌和已同步的消息立即删除。</li>
            <li><b>在 Facebook 中移除：</b>Facebook「设置和隐私 → 设置 → 商务集成」中选择 {product} 并移除，我们会收到通知并删除你接入的主页、Instagram 账号及其会话，并提供确认码供查询。</li>
            <li><b>邮件申请：</b>用注册邮箱发送到 <a href="mailto:{email}">{email}</a>，说明要删除的账号或企业，我们在 30 天内完成并邮件确认。</li>
            </ol>
            """;

}
