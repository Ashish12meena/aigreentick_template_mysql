-- ----------------------------------------------------------------------------
-- TEMPLATE LIBRARY SEED - 22 commonly used WhatsApp templates for system_templates.
--   UTILITY   (11): order confirmation / shipped / delivered, payment
--                 confirmation / reminder, refund, appointment confirmation /
--                 reminder, account welcome, password reset, subscription renewal
--   MARKETING (11): abandoned cart, promotional offer, flash sale, new arrivals,
--                 back in stock, festival greetings, birthday wishes, we miss
--                 you, event invitation, lead follow-up, feedback request
--
-- Requires 2026_09_26_template_library.sql (the system_templates table).
--
-- Each payload is in the CreateTemplateRequestDto shape ({template, variables}),
-- passes TemplateValidationService, and can be sent to POST /api/v1/templates
-- as is. name / language / category columns match payload.template.*.
--   - language is 'en' for every entry.
--   - Only TEXT headers, so no Meta media handle is needed (sample_media_url NULL).
--   - URLs use www.example.com; users replace them with their own on copy.
--   - variables[].label is the contact attribute key used at send time;
--     labelValue is the sample shown to Meta reviewers.
--   - No AUTHENTICATION entries: Meta writes the OTP body, so there is no
--     content to curate; users create OTP templates directly.
--
-- Safe to re-run: an existing (name, language) row is left untouched, so
-- entries edited through /internal/v1/template-library are not overwritten.
--
-- Run with the mysql client in the default sql_mode (backslash escapes ON):
-- newlines in the JSON are written as \\n.
-- ----------------------------------------------------------------------------

USE apargo_wa_messaging;

SET NAMES utf8mb4;
SET @now = UTC_TIMESTAMP(6);

START TRANSACTION;

INSERT INTO system_templates
  (name, language, category, description, sample_media_url, payload, is_active, created_at, updated_at)
VALUES
-- order_confirmation (UTILITY)
('order_confirmation',
 'en',
 'UTILITY',
 'Confirms a new order with order number, amount and a link to view it.',
 NULL,
 '{
  "template": {
    "name": "order_confirmation",
    "language": "en",
    "category": "UTILITY",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Order Confirmed"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, thank you for shopping with us!\\n\\nYour order *{{2}}* for *{{3}}* has been confirmed. We will let you know as soon as it ships.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "ORD12345",
              "Rs. 1,499"
            ]
          ]
        }
      },
      {
        "type": "FOOTER",
        "text": "Thank you for choosing us"
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "View order",
            "url": "https://www.example.com/orders/{{1}}",
            "example": [
              "https://www.example.com/orders/ORD12345"
            ]
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "order_id",
      "labelValue": "ORD12345",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "order_amount",
      "labelValue": "Rs. 1,499",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BUTTON",
      "variableIndex": 1,
      "label": "order_id",
      "labelValue": "ORD12345",
      "buttonIndex": 0,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- order_shipped (UTILITY)
('order_shipped',
 'en',
 'UTILITY',
 'Tells the customer their order has shipped, with courier, delivery date and tracking link.',
 NULL,
 '{
  "template": {
    "name": "order_shipped",
    "language": "en",
    "category": "UTILITY",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Your Order Is On The Way"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, good news! Your order *{{2}}* has been shipped via {{3}}.\\n\\nExpected delivery date: *{{4}}*. You can track your package using the button below.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "ORD12345",
              "Delhivery",
              "18 Oct 2026"
            ]
          ]
        }
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Track order",
            "url": "https://www.example.com/track/{{1}}",
            "example": [
              "https://www.example.com/track/AWB987654321"
            ]
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "order_id",
      "labelValue": "ORD12345",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "courier_name",
      "labelValue": "Delhivery",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 4,
      "label": "delivery_date",
      "labelValue": "18 Oct 2026",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BUTTON",
      "variableIndex": 1,
      "label": "tracking_id",
      "labelValue": "AWB987654321",
      "buttonIndex": 0,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- order_delivered (UTILITY)
('order_delivered',
 'en',
 'UTILITY',
 'Confirms delivery and lets the customer ask for help with one tap.',
 NULL,
 '{
  "template": {
    "name": "order_delivered",
    "language": "en",
    "category": "UTILITY",
    "components": [
      {
        "type": "BODY",
        "text": "Hi {{1}}, your order *{{2}}* has been delivered.\\n\\nWe hope you love it! If anything is not right, just reply to this message and our team will help you.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "ORD12345"
            ]
          ]
        }
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "QUICK_REPLY",
            "text": "Everything is fine"
          },
          {
            "type": "QUICK_REPLY",
            "text": "I need help"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "order_id",
      "labelValue": "ORD12345",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- payment_confirmation (UTILITY)
('payment_confirmation',
 'en',
 'UTILITY',
 'Acknowledges a payment received against an invoice.',
 NULL,
 '{
  "template": {
    "name": "payment_confirmation",
    "language": "en",
    "category": "UTILITY",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Payment Received"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, we have received your payment of *{{2}}* for invoice *{{3}}* on {{4}}.\\n\\nThank you for your business!",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Rs. 2,500",
              "INV-2026-001",
              "15 Oct 2026"
            ]
          ]
        }
      },
      {
        "type": "FOOTER",
        "text": "This is an automated message"
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "amount",
      "labelValue": "Rs. 2,500",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "invoice_number",
      "labelValue": "INV-2026-001",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 4,
      "label": "payment_date",
      "labelValue": "15 Oct 2026",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- payment_reminder (UTILITY)
('payment_reminder',
 'en',
 'UTILITY',
 'Reminds the customer of an upcoming or overdue payment, with a pay-now link.',
 NULL,
 '{
  "template": {
    "name": "payment_reminder",
    "language": "en",
    "category": "UTILITY",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Payment Reminder"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, this is a friendly reminder that your payment of *{{2}}* for invoice *{{3}}* is due on *{{4}}*.\\n\\nPlease ignore this message if you have already paid.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Rs. 2,500",
              "INV-2026-001",
              "20 Oct 2026"
            ]
          ]
        }
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Pay now",
            "url": "https://www.example.com/pay/{{1}}",
            "example": [
              "https://www.example.com/pay/INV-2026-001"
            ]
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "amount",
      "labelValue": "Rs. 2,500",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "invoice_number",
      "labelValue": "INV-2026-001",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 4,
      "label": "due_date",
      "labelValue": "20 Oct 2026",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BUTTON",
      "variableIndex": 1,
      "label": "invoice_number",
      "labelValue": "INV-2026-001",
      "buttonIndex": 0,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- refund_processed (UTILITY)
('refund_processed',
 'en',
 'UTILITY',
 'Confirms a refund has been processed and when it will reach the customer.',
 NULL,
 '{
  "template": {
    "name": "refund_processed",
    "language": "en",
    "category": "UTILITY",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Refund Processed"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, your refund of *{{2}}* for order *{{3}}* has been processed.\\n\\nThe amount will reflect in your original payment method within 5-7 business days.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Rs. 1,499",
              "ORD12345"
            ]
          ]
        }
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "refund_amount",
      "labelValue": "Rs. 1,499",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "order_id",
      "labelValue": "ORD12345",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- appointment_confirmation (UTILITY)
('appointment_confirmation',
 'en',
 'UTILITY',
 'Confirms a booked appointment with date, time and options to reschedule or cancel.',
 NULL,
 '{
  "template": {
    "name": "appointment_confirmation",
    "language": "en",
    "category": "UTILITY",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Appointment Confirmed"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, your appointment with *{{2}}* is confirmed for *{{3}}* at *{{4}}*.\\n\\nPlease arrive 10 minutes early. Need to make a change? Use the buttons below.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Dr. Mehta",
              "18 Oct 2026",
              "11:30 AM"
            ]
          ]
        }
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "QUICK_REPLY",
            "text": "Reschedule"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Cancel appointment"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "provider_name",
      "labelValue": "Dr. Mehta",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "appointment_date",
      "labelValue": "18 Oct 2026",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 4,
      "label": "appointment_time",
      "labelValue": "11:30 AM",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- appointment_reminder (UTILITY)
('appointment_reminder',
 'en',
 'UTILITY',
 'Reminds the customer of an upcoming appointment and asks them to confirm.',
 NULL,
 '{
  "template": {
    "name": "appointment_reminder",
    "language": "en",
    "category": "UTILITY",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Appointment Reminder"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, this is a reminder of your appointment on *{{2}}* at *{{3}}*.\\n\\nPlease let us know if you will be able to make it.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "18 Oct 2026",
              "11:30 AM"
            ]
          ]
        }
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "QUICK_REPLY",
            "text": "Yes, I will be there"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Reschedule"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "appointment_date",
      "labelValue": "18 Oct 2026",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "appointment_time",
      "labelValue": "11:30 AM",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- account_welcome (UTILITY)
('account_welcome',
 'en',
 'UTILITY',
 'Welcomes a customer after sign-up and explains what they will receive on WhatsApp.',
 NULL,
 '{
  "template": {
    "name": "account_welcome",
    "language": "en",
    "category": "UTILITY",
    "components": [
      {
        "type": "BODY",
        "text": "Hi {{1}}, welcome to *{{2}}*! Your account has been created successfully.\\n\\nYou will receive order updates and important account notifications on this number.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Green Mart"
            ]
          ]
        }
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "business_name",
      "labelValue": "Green Mart",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- password_reset (UTILITY)
('password_reset',
 'en',
 'UTILITY',
 'Sends a secure password reset link.',
 NULL,
 '{
  "template": {
    "name": "password_reset",
    "language": "en",
    "category": "UTILITY",
    "components": [
      {
        "type": "BODY",
        "text": "Hi {{1}}, we received a request to reset the password for your account.\\n\\nTap the button below to set a new password. If you did not request this, you can safely ignore this message.",
        "example": {
          "bodyText": [
            [
              "Rahul"
            ]
          ]
        }
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Reset password",
            "url": "https://www.example.com/reset-password/{{1}}",
            "example": [
              "https://www.example.com/reset-password/a1b2c3d4"
            ]
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BUTTON",
      "variableIndex": 1,
      "label": "reset_token",
      "labelValue": "a1b2c3d4",
      "buttonIndex": 0,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- subscription_renewal_reminder (UTILITY)
('subscription_renewal_reminder',
 'en',
 'UTILITY',
 'Reminds the customer that their subscription is about to expire, with a renew link.',
 NULL,
 '{
  "template": {
    "name": "subscription_renewal_reminder",
    "language": "en",
    "category": "UTILITY",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Subscription Expiring Soon"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, your *{{2}}* subscription will expire on *{{3}}*.\\n\\nRenew now to continue enjoying uninterrupted service.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Premium",
              "31 Oct 2026"
            ]
          ]
        }
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Renew now",
            "url": "https://www.example.com/renew"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "plan_name",
      "labelValue": "Premium",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "expiry_date",
      "labelValue": "31 Oct 2026",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- abandoned_cart (MARKETING)
('abandoned_cart',
 'en',
 'MARKETING',
 'Brings customers back to complete a purchase left in their cart.',
 NULL,
 '{
  "template": {
    "name": "abandoned_cart",
    "language": "en",
    "category": "MARKETING",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "You Left Something Behind"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, you left some items in your cart!\\n\\nYour *{{2}}* is still waiting for you. Complete your purchase now before it sells out.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Classic White Sneakers"
            ]
          ]
        }
      },
      {
        "type": "FOOTER",
        "text": "Reply STOP to unsubscribe"
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Complete purchase",
            "url": "https://www.example.com/cart"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Stop promotions"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "product_name",
      "labelValue": "Classic White Sneakers",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- promotional_offer (MARKETING)
('promotional_offer',
 'en',
 'MARKETING',
 'Classic discount offer with a coupon code and an expiry date.',
 NULL,
 '{
  "template": {
    "name": "promotional_offer",
    "language": "en",
    "category": "MARKETING",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Special Offer Just For You"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, enjoy *{{2}} off* on your next purchase!\\n\\nUse code *{{3}}* at checkout. Offer valid till {{4}}. Hurry, limited time only!",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "20%",
              "SAVE20",
              "31 Oct 2026"
            ]
          ]
        }
      },
      {
        "type": "FOOTER",
        "text": "Reply STOP to unsubscribe"
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Shop now",
            "url": "https://www.example.com/shop"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Stop promotions"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "discount",
      "labelValue": "20%",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "coupon_code",
      "labelValue": "SAVE20",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 4,
      "label": "valid_till",
      "labelValue": "31 Oct 2026",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- flash_sale (MARKETING)
('flash_sale',
 'en',
 'MARKETING',
 'Announces a short, time-bound sale.',
 NULL,
 '{
  "template": {
    "name": "flash_sale",
    "language": "en",
    "category": "MARKETING",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Flash Sale Is Live"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, our flash sale is now live!\\n\\nGet up to *{{2}} off* on {{3}}. The sale ends on {{4}}, so do not miss out.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "50%",
              "all footwear",
              "20 Oct 2026"
            ]
          ]
        }
      },
      {
        "type": "FOOTER",
        "text": "Reply STOP to unsubscribe"
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Shop the sale",
            "url": "https://www.example.com/sale"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Stop promotions"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "discount",
      "labelValue": "50%",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "product_category",
      "labelValue": "all footwear",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 4,
      "label": "sale_end_date",
      "labelValue": "20 Oct 2026",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- new_arrivals (MARKETING)
('new_arrivals',
 'en',
 'MARKETING',
 'Announces a new product collection.',
 NULL,
 '{
  "template": {
    "name": "new_arrivals",
    "language": "en",
    "category": "MARKETING",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "New Arrivals Are Here"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, our new *{{2}}* collection has just arrived!\\n\\nBe the first to explore the latest styles before they are gone.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Winter 2026"
            ]
          ]
        }
      },
      {
        "type": "FOOTER",
        "text": "Reply STOP to unsubscribe"
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Explore now",
            "url": "https://www.example.com/new-arrivals"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Stop promotions"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "collection_name",
      "labelValue": "Winter 2026",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- back_in_stock (MARKETING)
('back_in_stock',
 'en',
 'MARKETING',
 'Tells interested customers that a sold-out product is available again.',
 NULL,
 '{
  "template": {
    "name": "back_in_stock",
    "language": "en",
    "category": "MARKETING",
    "components": [
      {
        "type": "BODY",
        "text": "Hi {{1}}, good news! *{{2}}* is back in stock.\\n\\nIt sold out fast last time, so grab yours before it is gone again.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Classic White Sneakers"
            ]
          ]
        }
      },
      {
        "type": "FOOTER",
        "text": "Reply STOP to unsubscribe"
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Buy now",
            "url": "https://www.example.com/products/{{1}}",
            "example": [
              "https://www.example.com/products/classic-white-sneakers"
            ]
          },
          {
            "type": "QUICK_REPLY",
            "text": "Stop promotions"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "product_name",
      "labelValue": "Classic White Sneakers",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BUTTON",
      "variableIndex": 1,
      "label": "product_slug",
      "labelValue": "classic-white-sneakers",
      "buttonIndex": 0,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- festival_greetings (MARKETING)
('festival_greetings',
 'en',
 'MARKETING',
 'Festive greeting combined with a seasonal offer.',
 NULL,
 '{
  "template": {
    "name": "festival_greetings",
    "language": "en",
    "category": "MARKETING",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Festive Greetings"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, wishing you and your family a very happy {{2}}!\\n\\nCelebrate with us and enjoy *{{3}} off* on all orders this festive season.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Diwali",
              "25%"
            ]
          ]
        }
      },
      {
        "type": "FOOTER",
        "text": "Reply STOP to unsubscribe"
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Shop the sale",
            "url": "https://www.example.com/festive-sale"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Stop promotions"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "festival_name",
      "labelValue": "Diwali",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "discount",
      "labelValue": "25%",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- birthday_wishes (MARKETING)
('birthday_wishes',
 'en',
 'MARKETING',
 'Birthday greeting with a personal discount code.',
 NULL,
 '{
  "template": {
    "name": "birthday_wishes",
    "language": "en",
    "category": "MARKETING",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "Happy Birthday!"
      },
      {
        "type": "BODY",
        "text": "Dear {{1}}, wishing you a very happy birthday!\\n\\nTo make your day extra special, here is *{{2}} off* on your next order. Use code *{{3}}* before {{4}}.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "15%",
              "BDAY15",
              "31 Oct 2026"
            ]
          ]
        }
      },
      {
        "type": "FOOTER",
        "text": "Reply STOP to unsubscribe"
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Shop now",
            "url": "https://www.example.com/shop"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Stop promotions"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "discount",
      "labelValue": "15%",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "coupon_code",
      "labelValue": "BDAY15",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 4,
      "label": "valid_till",
      "labelValue": "31 Oct 2026",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- we_miss_you (MARKETING)
('we_miss_you',
 'en',
 'MARKETING',
 'Win-back message for inactive customers, with a return discount.',
 NULL,
 '{
  "template": {
    "name": "we_miss_you",
    "language": "en",
    "category": "MARKETING",
    "components": [
      {
        "type": "BODY",
        "text": "Hi {{1}}, we miss you! It has been a while since your last visit.\\n\\nHere is an exclusive *{{2}} off* on your next order, valid for the next 7 days.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "10%"
            ]
          ]
        }
      },
      {
        "type": "FOOTER",
        "text": "Reply STOP to unsubscribe"
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Shop now",
            "url": "https://www.example.com/shop"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Stop promotions"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "discount",
      "labelValue": "10%",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- event_invitation (MARKETING)
('event_invitation',
 'en',
 'MARKETING',
 'Invites customers to an event or webinar with a registration link.',
 NULL,
 '{
  "template": {
    "name": "event_invitation",
    "language": "en",
    "category": "MARKETING",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "You Are Invited!"
      },
      {
        "type": "BODY",
        "text": "Hi {{1}}, you are invited to *{{2}}*!\\n\\nDate: {{3}}\\nTime: {{4}}\\n\\nSeats are limited, so reserve your spot today.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Annual Customer Meetup",
              "25 Oct 2026",
              "6:00 PM"
            ]
          ]
        }
      },
      {
        "type": "FOOTER",
        "text": "Reply STOP to unsubscribe"
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "URL",
            "text": "Register now",
            "url": "https://www.example.com/events/register"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Stop promotions"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "event_name",
      "labelValue": "Annual Customer Meetup",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 3,
      "label": "event_date",
      "labelValue": "25 Oct 2026",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 4,
      "label": "event_time",
      "labelValue": "6:00 PM",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- lead_follow_up (MARKETING)
('lead_follow_up',
 'en',
 'MARKETING',
 'Follows up on an enquiry and offers a call back from the team.',
 NULL,
 '{
  "template": {
    "name": "lead_follow_up",
    "language": "en",
    "category": "MARKETING",
    "components": [
      {
        "type": "BODY",
        "text": "Hi {{1}}, thank you for your interest in *{{2}}*.\\n\\nWould you like one of our experts to call you and answer your questions?",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "Home Loan"
            ]
          ]
        }
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "QUICK_REPLY",
            "text": "Yes, call me"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Not now"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Stop promotions"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "product_name",
      "labelValue": "Home Loan",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now),

-- feedback_request (MARKETING)
('feedback_request',
 'en',
 'MARKETING',
 'Asks for a quick rating after an order or service.',
 NULL,
 '{
  "template": {
    "name": "feedback_request",
    "language": "en",
    "category": "MARKETING",
    "components": [
      {
        "type": "BODY",
        "text": "Hi {{1}}, thank you for your recent order *{{2}}*.\\n\\nHow was your experience with us? Your feedback helps us serve you better.",
        "example": {
          "bodyText": [
            [
              "Rahul",
              "ORD12345"
            ]
          ]
        }
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {
            "type": "QUICK_REPLY",
            "text": "Excellent"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Good"
          },
          {
            "type": "QUICK_REPLY",
            "text": "Needs improvement"
          }
        ]
      }
    ]
  },
  "variables": [
    {
      "componentType": "BODY",
      "variableIndex": 1,
      "label": "name",
      "labelValue": "Rahul",
      "buttonIndex": -1,
      "cardIndex": -1
    },
    {
      "componentType": "BODY",
      "variableIndex": 2,
      "label": "order_id",
      "labelValue": "ORD12345",
      "buttonIndex": -1,
      "cardIndex": -1
    }
  ]
}',
 1,
 @now,
 @now)
ON DUPLICATE KEY UPDATE id = id;

COMMIT;

-- Check:
-- SELECT id, name, language, category, is_active FROM system_templates ORDER BY category, name;
