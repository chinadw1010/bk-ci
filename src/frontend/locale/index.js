import VueI18n from 'vue-i18n'
import Vue from 'vue'
import { lang, locale } from 'bk-magic-vue'
import axios from 'axios'
const DEFAULT_LOCALE = 'en-US'
const loadedModule = {}

Vue.use(VueI18n)
    
const { messages, localeList } = importAll(require.context('@locale/nav/', false, /\.js/))

// export localeList
const i18n = new VueI18n({
    locale: DEFAULT_LOCALE,
    fallbackLocale: DEFAULT_LOCALE,
    messages
})

locale.i18n((key, value) => i18n.t(key, value))

function getLocalModuleId (module, locale) {
    return `${locale}_${module}`
}

function importAll (r) {
    let localeList = []
    const messages = r.keys().reduce((acc, key) => {
        const mod = r(key).default
        const matchLocaleKey = key.match(/\/([\w-]+)?\.js$/)
        const localeKey = (matchLocaleKey ? matchLocaleKey[1] : '')
        if (localeKey) {
            acc[localeKey] = {
                ...lang[localeKey.replace('-', '')],
                ...mod
            }
            localeList.push(localeKey)
        }
        return acc
    }, {});

    return {
        localeList,
        messages
    }
}

function setLocale (locale) {
    Object.keys(loadedModule).map(mod => {
        const [ , module ] = mod.split('_')
        if (!loadedModule[getLocalModuleId(module, locale)]) {
            dynamicLoadModule(module, locale)
        }
    })
    i18n.locale = locale
    axios.defaults.headers.common['Accept-Language'] = locale
    document.querySelector('html').setAttribute('lang', locale)
    return locale
}

function dynamicLoadModule (module, locale = DEFAULT_LOCALE) {
        
    const localeModuleId = getLocalModuleId(module, locale)
    if (loadedModule[localeModuleId]) {
        return
    }

    return import(`@locale/${module}/${locale}.js`).then(response => {
        const messages = response.default
        
        i18n.setLocaleMessage(locale, {
            ...i18n.messages[locale],
            [ module ]: messages
        })
        loadedModule[localeModuleId] = true
    })
}

export default {
    i18n,
    setLocale,
    localeList,
    dynamicLoadModule
}