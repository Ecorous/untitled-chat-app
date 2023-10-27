async function loadUserPage() {
    await globalLoad()
    let header = document.getElementById("welcome-header")
    let displayName = document.getElementById("displayName-input")
    let pronouns = document.getElementById("pronouns-input")
    let description = document.getElementById("bio-input")
    let token = await checkLogin()
    let resp = await fetch(root_data + "/user", {
        method: "GET",
        headers: {
            Authorization: token
        }
    })
    let json = await resp.json()
    if (resp.ok) {
        header.innerText = header.innerText.replaceAll("{}", json.displayName)
        displayName.value = json.displayName
        pronouns.value = json.pronouns
        description.value = json.description
    } else if (resp.status === 401) {
        logout()
    }
}

async function updateUser() {
    let displayName = document.getElementById("displayName-input")
    let pronouns = document.getElementById("pronouns-input")
    let description = document.getElementById("bio-input")
    let token = await checkLogin()
    let resp = await fetch(root_data + "/user", {
        method: "PUT",
        headers: {
            Authorization: token
        },
        body: JSON.stringify({
            displayName: displayName.value,
            pronouns: pronouns.value,
            description: description.value
        })
    })
    if (resp.ok) {
        window.location.reload()
    }
}