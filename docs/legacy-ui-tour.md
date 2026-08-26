# Legacy Java Pet Store UI tour

## Purpose

This is a quick, source-backed tour of what Java Pet Store 1.3.2 looked like.
It is deliberately not a claim that the original application was executed: its
J2EE 1.3 server, Java Web Start administration client, and obsolete repository
dependencies are not worth reviving merely to inspect the UI.

Evidence comes from the supplied legacy tree at
`/Users/adkunwar/Downloads/petstore1.3.2`, especially `docs/using.html` and
the storefront, administrator, and supplier JSP templates listed below.

## 1. Storefront home page

The customer storefront was a fixed-width, table-based page rather than a
responsive application. It used a small Helvetica/Arial font, a white
background, and dark teal (`#336666`) headings.

```text
+--------------------------------------------------------------------------+
| [Java Pet Store logo]                    [ search term ] [Search]       |
|                                            Account | Cart | Sign in       |
|                                            [EN] [JA] [ZH]                 |
+--------------------------------------------------------------------------+
| Pets                     | Pet Selection Map          | My List          |
| -----------------------  |                             | (only when       |
| Birds                    |       large parrot          | enabled in the   |
| Cats                     |    + small fish, bird,      | account profile) |
| Dogs                     |      dog, reptile, cat      |                  |
| Fish                     |                             |                  |
| Reptiles                 |                             |                  |
+--------------------------------------------------------------------------+
| Optional pet-tip/banner area                                              |
| Copyright footer                                                          |
+--------------------------------------------------------------------------+
```

The central image was a clickable image map: each animal region navigated to a
category. The original `splash.gif` is a parrot surrounded by fish, bird, dog,
reptile, and cat thumbnails. The header included a logo, search field, account,
cart, sign-in/out links, and English/Japanese/Chinese flags.

Source: `src/apps/petstore/src/docroot/template.jsp`, `banner.jsp`,
`sidebar.jsp`, `main.jsp`, `mylist.jsp`, and `images/splash.gif`.

## 2. Shopping path

The storefront flow was conventional but visually compact:

```text
Category list -> Product list -> Individual item -> Add to cart
                                                -> Cart -> Checkout form
                                                         -> Order completed
```

- **Category and product pages** were small table listings with animal images,
  product/item names, descriptions, prices, and links.
- **Cart** displayed the selected items, quantity controls, the total, and a
  checkout action.
- **Checkout** was a long data-entry page, split into *Billing Information*
  and *Shipping Information*. It pre-populated contact details from the
  account where possible.
- **Order completed** was a confirmation page; the legacy Storefront then sent
  the order asynchronously to the separate Order Processing Center (OPC).

The legacy guide illustrates the intended demo journey as: **Dogs -> Bulldog
-> Male Adult Bulldog -> Add to Cart -> Check Out -> Submit**.

Source: `docs/using.html`, `category.jsp`, `product.jsp`, `item.jsp`,
`cart.jsp`, `enter_order_information.jsp`, and `order_completed.jsp`.

## 3. Account screens

The account lifecycle was a full-page form and summary screen, presented in
small table rows rather than a dedicated settings area.

| Section | Visible legacy fields |
| --- | --- |
| Contact | First name, last name, two address lines, city, state/province, postal code, country, telephone, email |
| Payment | Card number, card type, expiry month/year |
| Preferences | English/Japanese/Chinese, favourite category, enable MyList, enable pet tips/banner |
| Account summary | The same contact, payment, and preference values plus an **Edit Your Account Information** link |

The legacy account-summary JSP rendered the credit-card number directly. The
modernized application intentionally does **not** carry this design forward:
payment-card capture/storage needs a PCI-compliant payment provider and token,
not a customer profile field.

Source: `create_customer.jsp`, `customer.jsp`, `edit_customer.jsp`,
`signon.jsp`, and `duplicate_account.jsp`.

## 4. Administrator UI: a different kind of application

The administrator did not use the storefront UI. Its browser landing page was
plain HTML: a centred **Java Pet Store Admin Page** heading, explanatory text,
and a **Launch Rich Client** submit button. That launched a Java Web Start
Swing/JFC application.

```text
Browser landing page             Java Web Start rich client
-------------------              ---------------------------------
Java Pet Store Admin Page   ->   [Pending orders] [Non-pending orders]
Launch Rich Client                Select order -> approve / deny -> Commit
Logout                            Refresh sales, revenue, and order state
```

The rich client separated pending orders from approved, denied, or fulfilled
orders. Orders above $500 became pending; an administrator selected a status
and committed the changes.

Source: `src/apps/admin/src/docroot/index.jsp`,
`docs/using.html`, and `src/apps/admin/src/client/resources/`.

## 5. Supplier UI

The supplier portal was also separate from the storefront. It was a basic
HTML/JSP inventory screen titled **Inventory Update Page**.

```text
Item ID | Existing Quantity | New Quantity [      ] | Update [ ]
Item ID | Existing Quantity | New Quantity [      ] | Update [ ]
                                                     [ Submit ]
```

An administrator entered new quantities, checked the rows to change, and
submitted the whole form. The supplier then notified the OPC; the OPC could
complete previously unfulfillable orders after an inventory change.

Source: `src/apps/supplier/src/docroot/index.jsp`, `login.jsp`, and
`displayinventory.jsp`; behaviour described in `docs/using.html`.

## What you can compare directly today

| Legacy UI | Current modernized UI |
| --- | --- |
| Fixed table layout, image map, JSP/WAF templates | Same-origin responsive browser UI at `http://localhost:8080` |
| Search, category browsing, product/item detail, cart and checkout | Catalog, cart, checkout, order history, and explicit concurrency/error handling |
| Full-page customer form, including unsafe raw card storage | Persisted account registration/profile with BCrypt passwords, contact/default address, language/category, MyList/banner preferences; no raw card data |
| Account/cart links in the banner | Dedicated Account experience in the modern UI |
| Separate Java Web Start admin and supplier applications | Responsive `/admin/orders.html` approval queue and `/supplier/` inventory/PO portal, with independent roles and protected APIs |
| No integrated operational view for customers or operators | Protected health, logs, request/database telemetry, and query diagnostics dashboard |

## What this tour is useful for

This tour makes the visual and workflow heritage concrete without implying that
the whole legacy distribution is already modernized. It is enough to compare
the original customer experience with the running modern storefront; running
the actual J2EE 1.3 distribution remains optional archaeology, not a dependency
of the modernization.
