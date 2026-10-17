const { test } = require('node:test')
const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const { runInNewContext } = require('node:vm')

// Fixtures represent separate identities sharing an email with different capitalization.
const original = {
    user_id: 'auth0|original',
    email: 'Alex@example.com',
    created_at: '2026-01-01T00:00:00Z',
}

const later = {
    user_id: 'google-oauth2|later',
    email: 'alex@example.com',
    created_at: '2026-02-01T00:00:00Z',
}

// Execute the actual Action source with fake Auth0 services; no network requests are made.
async function execute(kind, {
    users = [],
    current = original,
    failure = false,
    client = 'app',
    secrets = {},
    searchOnly = false,
} = {})
{
    const exported = {}
    const requests = []
    const denials = []
    const logs = []

    const source = readFileSync(join(__dirname, '..', 'actions', kind === 'registration'
        ? 'prevent-duplicate-registration.js' : 'restrict-duplicate-login.js'), 'utf8')

    // Capture requests, denials, and logs in a fresh JavaScript context for each run.
    runInNewContext(source, {
        exports: exported,
        URLSearchParams,
        AbortSignal,
        console: { error: (...args) => logs.push(args) },
        fetch: async (url, options) => {
            requests.push({ url, options })

            if (failure)
            {
                return { ok: false, status: 429 }
            }

            // Simulate token issuance, user lookups, and case-insensitive-search-only matches.
            const result = url.endsWith('/oauth/token') ? { access_token: 'test-token' }
                : searchOnly && url.includes('/users-by-email?') ? [] : users

            return { ok: true, json: async () => result }
        },
    })

    const event = {
        client: { client_id: client },
        user: current,
        secrets: {
            APP_CLIENT_ID: 'app',
            AUTH0_DOMAIN: 'example.auth0.com',
            MANAGEMENT_CLIENT_ID: 'test-client',
            MANAGEMENT_CLIENT_SECRET: 'test-secret',
            ...secrets,
        },
    }

    await exported[kind === 'registration' ? 'onExecutePreUserRegistration' : 'onExecutePostLogin'](
        event, { access: { deny: (...args) => denials.push(args) } })

    return { requests, denials, logs }
}

// Cover normal signup and login decisions, including either provider being registered first.
test('new email registration is allowed', async () => {
    assert.equal((await execute('registration')).denials.length, 0)
})

test('existing Google identity blocks password registration, ignoring case', async () => {
    const result = await execute('registration', { users: [later], current: original, searchOnly: true })

    assert.equal(result.denials[0][0], 'email_already_registered')

    const query = new URL(result.requests.at(-1).url).searchParams
    assert.equal(query.get('q'), 'email:"alex@example.com"')
})

test('oldest identity remains allowed when a duplicate exists', async () => {
    assert.equal((await execute('login', { users: [later, original] })).denials.length, 0)
})

test('later social identity is denied even if search has not indexed it', async () => {
    const result = await execute('login', { users: [original], current: later })

    assert.match(result.denials[0][0], /original sign-in method/)
})

test('Google first remains allowed and a later password identity is denied', async () => {
    const google = { ...original, user_id: 'google-oauth2|first' }
    const password = { ...later, user_id: 'auth0|second' }

    assert.equal((await execute('login', { users: [google, password], current: google })).denials.length, 0)
    assert.equal((await execute('login', { users: [google, password], current: password })).denials.length, 1)
})

test('current identity is allowed before it appears in search', async () => {
    assert.equal((await execute('login')).denials.length, 0)
})

// Both triggers must respect app scope and deny access when the lookup cannot be trusted.
for (const kind of ['registration', 'login'])
{
    test(`${kind}: unrelated applications are not checked`, async () => {
        const result = await execute(kind, { client: 'another-app' })

        assert.equal(result.requests.length, 0)
        assert.equal(result.denials.length, 0)
    })

    test(`${kind}: lookup errors are logged and deny access`, async () => {
        const result = await execute(kind, { failure: true })

        assert.equal(result.logs.length, 1)
        assert.match(result.denials[0].at(-1), /try again later/)
    })

    test(`${kind}: missing configuration denies rather than bypassing`, async () => {
        assert.equal((await execute(kind, { secrets: { APP_CLIENT_ID: '' } })).denials.length, 1)
        assert.equal((await execute(kind, { secrets: { MANAGEMENT_CLIENT_SECRET: '' } })).denials.length, 1)
    })

    test(`${kind}: incomplete search results deny access`, async () => {
        assert.equal((await execute(kind, { users: Array(100).fill(original) })).denials.length, 1)
    })

    test(`${kind}: missing email denies access`, async () => {
        assert.equal((await execute(kind, { current: { ...original, email: undefined } })).denials.length, 1)
    })
}

// Invalid ownership-ordering data must not select an arbitrary original account.
test('missing creation dates deny login instead of choosing an arbitrary owner', async () => {
    assert.equal((await execute('login', { users: [{ ...later, created_at: undefined }] })).denials.length, 1)
})

test('nonmatching search results do not block registration', async () => {
    assert.equal((await execute('registration', { users: [{ ...later, email: 'someone@example.com' }] })).denials.length, 0)
})
