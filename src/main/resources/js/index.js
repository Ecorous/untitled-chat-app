root_data = window.location.protocol + "//" + window.location.host

async function onjsload() {
    await globalLoad()
    let wawaHeader = document.getElementById("wawa_header")
    let token = await checkLogin()
    let resp = await fetch(root_data + "/user", {
        method: "GET",
        headers: {
            Authorization: token
        }
    })
    let json = await resp.json()
    if (resp.ok) {
        wawaHeader.innerText = `hey, ${json.displayName}!`
        document.getElementById("login-div").style.display = "none"
        document.getElementById("logout").style.display = "unset"
    } else {
        localStorage.removeItem("token")
    }
}

async function globalLoad() {
    lucide.createIcons()
}

async function checkLogin() {
    let localToken = localStorage.getItem("token")
    if (localToken == null) {
        window.location.href = `${root_data}/web/login`
    } else {
        return localToken
    }
}

function logout() {
    localStorage.removeItem("token")
    window.location.reload()
}