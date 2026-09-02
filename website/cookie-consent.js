/**
 * Freerouting Cookie Consent & Google Consent Mode v2 Manager
 * Compliant with EU GDPR, ePrivacy Directive, and Swiss FADP.
 */
(function() {
    'use strict';

    // Initialize Google dataLayer and consent defaults
    window.dataLayer = window.dataLayer || [];
    function gtag() { window.dataLayer.push(arguments); }
    window.gtag = window.gtag || gtag;

    var STORAGE_KEY = 'freerouting_cookie_consent';
    var storedConsent = null;
    try {
        storedConsent = localStorage.getItem(STORAGE_KEY);
    } catch (e) {
        // LocalStorage disabled / restricted
    }

    // Set default consent state before analytics initialization
    if (storedConsent === 'granted') {
        gtag('consent', 'default', {
            'analytics_storage': 'granted',
            'ad_storage': 'denied',
            'ad_user_data': 'denied',
            'ad_personalization': 'denied'
        });
    } else {
        gtag('consent', 'default', {
            'analytics_storage': 'denied',
            'ad_storage': 'denied',
            'ad_user_data': 'denied',
            'ad_personalization': 'denied'
        });
    }

    function updateConsent(status) {
        try {
            localStorage.setItem(STORAGE_KEY, status);
        } catch (e) {}

        if (status === 'granted') {
            gtag('consent', 'update', {
                'analytics_storage': 'granted'
            });
        } else {
            gtag('consent', 'update', {
                'analytics_storage': 'denied'
            });
        }
        hideBanner();
    }

    function hideBanner() {
        var banner = document.getElementById('fr-cookie-banner');
        if (banner) {
            banner.style.display = 'none';
        }
    }

    function showBanner() {
        var banner = document.getElementById('fr-cookie-banner');
        if (!banner) {
            banner = document.createElement('div');
            banner.id = 'fr-cookie-banner';
            banner.className = 'cookie-banner';
            banner.setAttribute('role', 'dialog');
            banner.setAttribute('aria-live', 'polite');
            banner.setAttribute('aria-label', 'Cookie Consent');
            banner.innerHTML = [
                '<div class="cookie-banner-content">',
                '  <div class="cookie-banner-text">',
                '    <p><strong>Cookie & Privacy Settings:</strong> We use essential technical cookies to ensure the proper operation of our website, and optional Google Analytics cookies to understand website usage and improve Freerouting. We do not use advertising or tracking cookies. You can accept or decline non-essential cookies at any time. For more information, please read our <a href="privacy.html">Privacy Policy</a>.</p>',
                '  </div>',
                '  <div class="cookie-banner-actions">',
                '    <button type="button" id="fr-cookie-accept" class="cookie-btn cookie-btn-accept">Accept All</button>',
                '    <button type="button" id="fr-cookie-decline" class="cookie-btn cookie-btn-decline">Decline Non-Essential</button>',
                '  </div>',
                '</div>'
            ].join('');
            document.body.appendChild(banner);

            document.getElementById('fr-cookie-accept').addEventListener('click', function() {
                updateConsent('granted');
            });
            document.getElementById('fr-cookie-decline').addEventListener('click', function() {
                updateConsent('denied');
            });
        }
        banner.style.display = 'block';
    }

    // Expose method to re-open settings from footer
    window.openCookiePreferences = function() {
        showBanner();
    };

    // Auto-show banner if no preference recorded yet
    document.addEventListener('DOMContentLoaded', function() {
        if (!storedConsent) {
            showBanner();
        }
    });
})();
