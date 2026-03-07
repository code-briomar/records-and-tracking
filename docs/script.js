const REPO_OWNER = 'code-briomar';
const REPO_NAME = 'records-and-tracking';

async function fetchLatestRelease() {
    try {
        const response = await fetch(`https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/latest`);
        if (!response.ok) throw new Error('Failed to fetch release');
        return await response.json();
    } catch (error) {
        console.error('Error fetching release:', error);
        return null;
    }
}

function formatFileSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', { 
        year: 'numeric', 
        month: 'long', 
        day: 'numeric' 
    });
}

async function updateDownloadInfo() {
    const release = await fetchLatestRelease();
    
    if (!release) {
        document.getElementById('latest-version').textContent = 'v0.1.11 (fallback)';
        document.getElementById('release-date').textContent = 'March 2, 2026';
        return;
    }

    const version = release.tag_name.replace('v', '');
    const releaseDate = formatDate(release.published_at);

    document.getElementById('latest-version').textContent = `v${version}`;
    document.getElementById('release-date').textContent = releaseDate;

    const msiAsset = release.assets.find(asset => 
        asset.name.toLowerCase().endsWith('.msi')
    );

    const downloadBtn = document.getElementById('download-btn');
    const downloadSize = document.getElementById('download-size');
    const noMsiMessage = document.getElementById('no-msi');
    const heroBtn = document.getElementById('hero-download-btn');

    if (msiAsset) {
        const downloadUrl = msiAsset.browser_download_url;
        const fileSize = formatFileSize(msiAsset.size);
        
        downloadBtn.href = downloadUrl;
        downloadSize.textContent = `(${fileSize})`;
        downloadBtn.style.display = 'inline-flex';
        heroBtn.href = downloadUrl;
        noMsiMessage.style.display = 'none';
    } else {
        downloadBtn.style.display = 'none';
        heroBtn.href = '#download';
        heroBtn.onclick = function(e) {
            e.preventDefault();
            document.getElementById('download').scrollIntoView({ behavior: 'smooth' });
        };
        noMsiMessage.style.display = 'block';
    }
}

document.addEventListener('DOMContentLoaded', updateDownloadInfo);
