# Ready Ground — Prepper Dropshipping: Everything In One Place

This file collects everything produced for the prepper / emergency-preparedness dropshipping project (brand: **Ready Ground**). It pulls together the Claude sessions, published artifacts, repos and supplier email threads from July to September 2026.

_Last compiled: 23 Sep 2026_

---

## 1. At a glance

| | |
|---|---|
| **Business** | Ready Ground: a UK-based preparedness content site plus dropship store, selling worldwide (the US is the biggest market) |
| **Categories** | Water storage, food storage, first aid, off-grid power, bug-out bags, home security |
| **Recommended start** | Direction A, "Narrow & Safe": one non-restricted sub-niche (water filtration or get-home bag), pure dropship, standard payment processor, growth led by SEO and content |
| **Content engine** | Blackout Ready: a free 72-hour checklist as the lead magnet, a £5–9 paid guide, and 5 faceless video scripts |
| **Supplier status** | 10 suppliers contacted, 2 live leads (Liberty Mountain, SunGoldPower), 2 declined dropship, 6 have not replied |
| **Biggest blockers** | No live store URL yet (SunGoldPower asked for one), no business or tax documents yet, and the Remotion explainer source was never pushed |

---

## 2. Where everything lives

### Published artifacts (private, claude.ai)
| Asset | Link | Status |
|---|---|---|
| **Ready Ground HQ** (the hub that links everything below) | https://claude.ai/code/artifact/1e75a11e-3e11-4c65-8081-b75ab0aff8bf | Live |
| **Prepper Commerce: Market Deep-Dive & Dropshipping Roadmap** (15 sections, cited sources) | https://claude.ai/code/artifact/6fa89145-2f74-4fd4-8d5e-51425c852f2e | Live |
| **Blackout Ready: Week 1 Content Kit** (checklist, paid guide, 5 scripts) | https://claude.ai/code/artifact/7127873d-cd45-4668-a62e-627d41dd873b | Live |

### Claude sessions
| Session | Date | What happened |
|---|---|---|
| Prepper website dropshipping explainer (`session_01Ce16LBFMQrDBX4mHxhHfnm`, repo `6900-sudo/gstack`) | Jul–Aug 2026 | Produced the market deep-dive, the Blackout Ready kit and the Remotion explainer source. **Stalled on a question about pushing.** Branch `claude/prepper-dropshipping-video-ldt9q3` was never pushed; only the README reached `gstack/main` at `prepper-dropshipping-video/README.md`. |
| Bunker essentials explainer video (`session_01JXqWonh6UBGTtcwiG82wBc`, repo `6900-sudo/social-media-skills`) | 25 Jul 2026 | Bunker-essentials video delivered, with a Remotion preview. PR #2 merged. |
| Ready Ground dropshipping site (`session_01EajB12A2xHmBTKco4N5UQK`, repo `6900-sudo/Open-Generative-AI`) | 11 Sep 2026 | Built the Ready Ground HQ hub. Ended "awaiting business setup". |
| Supplier outreach | 6–31 Aug 2026 | Request-for-information (RFI) emails and follow-ups sent from Gmail (section 5). |

---

## 3. Market research: key takeaways

Full detail and sources are in the Market Deep-Dive artifact.

**Demand signals**
- About 20–26M adults in the US self-identify as preppers, roughly double the 2017 figure. About 30% of US households show prepper behaviour (FEMA 2023).
- Survival kits are forecast to grow at about 7.8% a year, and online stores are the fastest-growing channel (about 8.4% a year).
- Demand is seasonal. It spikes around disasters, elections and inaugurations; My Patriot Supply's orders roughly double in inauguration weeks.
- Published "market size" figures disagree by 5–10x ($1.3B–$8.7B). Treat them as rough direction, not fact.

**Unit economics**
- Gross margin is typically 65–70%. After ads and customer acquisition cost (20–35% of revenue), realistic **net margin is 10–20%**. Below about 10%, there is no buffer.
- First-90-day budgets: **Bootstrap $800–1.5k · Standard $2–4k (the realistic minimum) · Aggressive $5–10k+**.
- Recurring costs: Shopify $29–39/mo, apps $30–300/mo, Klaviyo from $20/mo, **product liability insurance $42–99/mo (not optional)**, ads $300+/mo before the results tell you anything.

**Three watch-outs that sink stores in this niche**
1. **Payment processors.** Stripe bans tactical and hunting knives, and PayPal and Stripe treat survivalist merchants as high-risk. Funds can be frozen for up to 180 days. To avoid this, stick to food, water, first aid, power, comms and non-blade EDC (everyday-carry) gear.
2. **Duty-free thresholds are gone.** The US $800 de minimis exemption ended for China on 2 May 2025 and for all countries on 29 Aug 2025. The EU's €150 threshold ended on 1 Jul 2026 and was replaced by a €3 flat duty. Cheap China-direct dropshipping no longer works on the old numbers, so **use regional or US-warehouse fulfilment**.
3. **Product liability.** You are the seller of record. Never claim "NSF certified" or quote a shelf life without the supplier's documents in writing. Order samples of everything before you list it.

**Category risk matrix**

| Category | Regulatory | Payment risk | Verdict |
|---|---|---|---|
| Food storage / freeze-dried | Medium | Low | Start here |
| Water filtration | Medium | Low | Start here |
| First aid | Medium | Low | Yes |
| Power (solar, power banks) | Medium | Low | Yes (lithium batteries need hazmat shipping documents) |
| Radios / comms | Low | Low | Yes |
| EDC / non-blade tools | Low | Low | Yes |
| Knives / blades | High | High | **Avoid at launch** |
| Self-defence sprays | High | High | **Avoid at launch** |

**UK seller, three tax regimes**
- **UK:** VAT registration is required above £90k turnover. A Ltd company is recommended because it limits your personal liability.
- **US:** You can owe state sales tax even with no US presence. The usual threshold is $100k or 200 transactions a year. **CA, FL, HI, IL, MD and MA don't accept the supplier's resale certificate on drop-shipped orders**, so you may have to register there sooner. Your US federal tax position also needs a cross-border accountant.
- **EU:** Register for IOSS (the EU's import VAT scheme) through HMRC or an intermediary. For GB and EU electronics, the CE mark is the practical route.
- Launch in **one market first**, not all three at once.

**Competitors.** My Patriot Supply, Legacy, ReadyWise and Augason Farms are all manufacturers, so you can't beat them on price. You can win with sub-niches they ignore, such as renters or apartments, pets, get-home bags and car-camping crossover, or by building an audience first.

---

## 4. Strategy and roadmap

**Four directions** (start with A or C, then move toward B once you have sales data)
- **A. Narrow & Safe (recommended):** one sub-niche, pure dropship, SEO and content.
- **B. Hybrid Hero-SKU:** a broad dropship catalogue plus 1–3 proven bestsellers bought in bulk.
- **C. Content-First Flywheel:** build an audience first. The Blackout Ready kit was made for this route.
- **D. Full catalogue including tactical:** needs a high-risk payment processor and state-aware checkout. Only after A or B is proven.

**6-phase launch**

| When | Phase | Key actions |
|---|---|---|
| Weeks 1–2 | 0 Validate | Pick one sub-niche, check search demand, contact 3–5 suppliers, **order samples** |
| Weeks 2–4 | 1 Legal | Ltd company, UTR/EIN, business bank account, insurance, IOSS, check which US states need registration |
| Weeks 3–6 | 2 Store build | Platform, product pages written from supplier documents, FTC policy pages (shipping, returns, refunds) |
| Weeks 5–8 | 3 Suppliers | Supplier app, 10–25 SKUs, test orders to your own address |
| Weeks 6–10 | 4 Soft launch | Friends and family plus organic channels, SEO, email list with the checklist as lead magnet, ads only in allowed categories |
| Month 3+ | 5 Iterate | Owned inventory for the winners, subscriptions for consumables (food rotation, filter cartridges) |

**Marketing limits.** Meta bans ads for knives and weapons. TikTok bans ads for pepper spray, tasers and police or military gear. Doomsday-style fear marketing carries FTC risk, so keep claims specific and defensible.

---

## 5. Supplier outreach tracker (from Gmail)

| Supplier | Category | Contacted | Outcome | Next step |
|---|---|---|---|---|
| **Liberty Mountain** (sales@libertymountain.com) | Outdoor/survival distributor (referred by Brunton) | 15 Aug, follow-up 17 Aug | ✅ **Replied 18 Aug:** complete the dealer account application (PDF attached, or the online form at libertymountain.com/customer-application). Takes 3–5 business days. They didn't confirm dropship; that gets answered after the application. | **Fill in the application** |
| **SunGoldPower** (Joyce, sales06@sungoldpower.com) | Solar kits, power stations, inverters | 15 Aug, follow-up 17 Aug | ✅ **Replied 19 Aug.** They want: (1) the store's website URL, (2) your timeline for uploading their products and your launch date, (3) your operating strategy for their product line, (4) a **reseller business licence**, (5) a **tax-exempt certificate**, (6) the attached dealer application form. They ship within 24 hours from a California warehouse. | Needs a live site and paperwork (see note below) |
| Brunton (sales@brunton.com) | Navigation / outdoor | 6 Aug | ❌ No dropship. Referred you to Liberty Mountain and Blue Ridge Knives. | Go through Liberty Mountain |
| Blue Ridge Knives (onestop@brk.com) | Knives | 15 Aug, 17 Aug | ❌ No dropship, dealers only (2 auto-replies). | Drop it: knives are "avoid at launch" anyway |
| Mayday Industries (purchase@maydayindustries.com) | Emergency kits / food / water | 15, 17, 31 Aug | No reply after 3 emails | Phone them or drop |
| Tactical Dropship (Dropshipping@tacticaldropship.com) | Pre-packed bug-out bags | 15, 17, 31 Aug | No reply after 3 emails | Phone them or drop |
| ViTAC Solutions (info@vitacsolutions.com) | IFAK / trauma kits | 15, 17, 31 Aug | No reply after 3 emails | Phone them or drop |
| Survival Dropship (Dropship@survivaldropship.com) | Broad prepper catalogue | 6, 17 Aug | No reply | One final follow-up |
| Peak 10 Co (b2b@peak10co.com) | B2B reseller | 6, 17 Aug | No reply | One final follow-up |
| Survival Frog (james@survivalfrog.com) | Broad prepper catalogue | 6, 17 Aug | No reply | One final follow-up |

> **Note on SunGoldPower's paperwork:** a "tax-exempt certificate" usually means a US state resale certificate. As a UK business you may not have one. Ask whether they accept a UK company registration number and VAT registration (or a multi-state or foreign-seller exemption form) instead. SunGoldPower also only mentions California fulfilment, so they would cover US customers only.

Aggregator options named in the research, which you can use without applying to each brand: **CJ Dropshipping** (China plus US warehouses), **Zendrop** (US fulfilment), **Spocket** (US/EU/AU suppliers, 2–7 day shipping).

---

## 6. Content assets

**Blackout Ready: Week 1 Content Kit**
- **Free lead magnet:** the 72-Hour Blackout Checklist (torch, power bank, radio, 5 L water per person per day for 3 days, no-cook food and a tin opener, warm layers, first aid and prescriptions, cash, and a carbon-monoxide warning). Host it on MailerLite, Beehiiv or Gumroad.
- **Paid product:** *Blackout Ready: The Full Guide*, £5–9 on Gumroad or Etsy (both handle UK/EU VAT). It covers water quantities, fridge and freezer timings (4 h / 48 h / 24 h and the 2-hour rule), CO safety, a spending plan (£20, £50, £100 tiers) and a two-week upgrade.
- **5 faceless video scripts** (9:16, calm voice, subtitles on, AI-generated label ticked):
  1. *The First 72 Hours of a Power Cut*: hook "3 DAYS. NO POWER."
  2. *You Have Less Drinkable Water Than You Think*: hook "YOUR TAPS CAN STOP TOO"
  3. *Your Fridge Has a 4-Hour Clock*: hook "DON'T OPEN THE FRIDGE"
  4. *A Power-Cut Kit for Twenty Pounds*: hook "£20. THAT'S IT."
  5. *The Power-Cut Mistake That Kills*: hook "NEVER DO THIS INDOORS"
- The content loop: a video gives value, the free checklist grows the email list, and the paid guide earns.

**Remotion explainer video: "How prepper dropshipping works"**
- 1080×1920, 30 fps, 1,150 frames (about 38 s). Five scenes: Hook ($2B+ market, $0 inventory), Niche (water, food, first aid), then Build Store, then Pricing ($10 cost, $29.99 price, +199%), then Launch.
- ⚠ **Source not recovered.** It was fully written in `session_01Ce16LBFMQrDBX4mHxhHfnm` but the branch was never pushed. Only the spec README is on `gstack/main`. To get it back, reopen that session and push, or rebuild it from the README.

**Bunker essentials explainer video.** Delivered through `6900-sudo/social-media-skills` PR #2 (merged).

---

## 7. Open actions (in priority order)

1. **Decide the sub-niche and the first market** (Direction A plus US or UK). Every supplier asks about this.
2. **Put up a minimal live site or landing page.** SunGoldPower won't review without a URL, and the checklist lead magnet needs somewhere to live.
3. **Business paperwork:** Ltd company, business bank account, insurance quote, and your answer on the resale or tax-exempt certificate.
4. **Liberty Mountain:** submit the dealer application.
5. **SunGoldPower:** reply to Joyce with the URL, timeline, strategy, documents and form.
6. **Unanswered suppliers:** phone Mayday, Tactical Dropship and ViTAC, or drop them. Send one final nudge to Survival Dropship, Peak 10 and Survival Frog. Look at CJ, Zendrop and Spocket as a fallback.
7. **Content:** publish the checklist and guide on Gumroad, and post the 5 Blackout Ready videos.
8. **Recover the Remotion explainer source** from the stalled gstack session.
