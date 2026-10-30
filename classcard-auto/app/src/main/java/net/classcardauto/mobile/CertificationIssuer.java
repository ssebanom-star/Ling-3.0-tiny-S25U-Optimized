package net.classcardauto.mobile;

/**
 * Identity of whoever may issue authorisation files for this build.
 * Both values come from the issuer tool (issuer/keygen.py); only the public half is ever put in the app.
 * (The values of the original build were replaced with this project's own issuer.)
 */
public final class CertificationIssuer {
    public static final CertificationIssuer INSTANCE = new CertificationIssuer();
    public static final String MACHINE_ID = "de836c3fc1a3f7a0937dd70e36622bd95480879a5c08e204f51d43ea34d1ba43";
    public static final String PUBLIC_KEY = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEArdftjc+YYV0wj7zTy2WAS+EPA+esR0MIZUESLzaud3/PMEl3jwN/xq6Wud3BvoK07gdXvrgPv4+KPYtmcc643LLRN93whD236ugH2nImDazvHUclHGz1ZRUxTNbsqE/+qWCgSiWGVrvkSghNG/7i7hKz0gNVhyXIxfmdGJyY3XgNUQvIghx91CTvFhz/t4Dh7ylsWS4c66aEu+atwdxBlSrWwqtk9IPnIS416MWG3QKYEl+DuyzOrxV+natkfqX+PT7Vrdayhnu99mDjve7KmfC0hHPfMqX22f/z0GEfHZfvrvgDS6ls9xalSCeR9JRZIG+eg4C+iiixhPvfV4ePtRuvuD3OikUCE6oUEAtuK2V2m9EViAMIOj/1aAmlXAHS3OqsypNgUytFw2wjz1NMwpMeZp1q2mNn8jBhvUS2kUEmjjIivG5RTynSMlecK+s+MEUJm7XeJrIOHri7OQo8j6/2HI8K/4BgZuR3iR+OrEY8inEKN+P5h9JYyK2ZbEbdAgMBAAE=";

    /**
     * Public keys (X.509 EC P-256, base64) of owner phones that are trusted issuers by build: such a phone needs no
     * issuer certificate from the computer and issues short activation codes ("MC2."). Empty until a phone's key is
     * added here (the key comes from that phone's 발급자 도구 > 요청 복사 text, field "issuerPub").
     */
    public static final String[] PHONE_ROOTS = {};

    private CertificationIssuer() {
    }
}
