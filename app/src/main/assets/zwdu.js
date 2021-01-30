// 需要传递到外部的数据(必要)
var baseObject = {
	info: {
		origin: 'https://www.zwdu.com',
		type: 'book',
		site: '💮八一中文网',
		group: '更新快;无错字'
	},
	search: [{ title: '书名', author: '作者', intro: '简介', tag: '分类', count: '字数', img: '封面', date: '更新日期', last: '最新章节', url: '详情页' }],
	detail: { title: '书名', author: '作者', intro: '简介', tag: '分类', count: '字数', img: '封面', date: '更新日期', last: '最新章节', url: '目录页' },
	chapters: [{ title: '标题', time: '更新日期', url: '正文页' }],
	context: ''
};

var debugTime = new Date();
var printLog = (msg, start) => {
	let thisTime = new Date();
	if (start) debugTime = thisTime;
	console.info(new Date(thisTime - debugTime).format('[mm:ss.fff]') + msg);
}

// 搜索页
function search(searchKey) {
	printLog(`开始搜索关键字 ${searchKey}\n${baseObject.info.origin}/search.php?keyword=${searchKey}`, true);
	let response = fetch(`${baseObject.info.origin}/search.php?keyword=${searchKey}`);
	let html = response.text();
	let document = new Document(html);
	printLog('成功获取结果');

	baseObject.search = [];
	let searchList = document.querySelectorAll('.result-list>div');
	let titleList = searchList.queryAllText('h3>a');
	printLog(`解析到 ${titleList.length} 个结果`);
	let authorList = searchList.queryAllText('.result-game-item-info>p:nth-child(1)>span:last-child').map((T) => T.trim());
	let introList = searchList.queryAllText('.result-game-item-desc');
	let tagList = searchList.queryAllText('.result-game-item-info>p:nth-child(2)>span:last-child');
	//let countList = '';
	let imgList = searchList.queryAllAttr('img', 'src');
	let dateList = searchList.queryAllText('.result-game-item-info>p:nth-child(3)>span:last-child');
	let lastList = searchList.queryAllText('.result-game-item-info>p:nth-child(4)>a').map((T) => T.trim());
	let urlList = searchList.queryAllAttr('h3>a', 'href');
	for (let i = 0, n = titleList.length; i < n; ++i) {
		baseObject.search.push({
			title: titleList[i],
			author: authorList[i],
			intro: introList[i],
			tag: tagList[i],
			countList: '',
			img: imgList[i],
			date: dateList[i],
			lastList: lastList[i],
			url: urlList[i]
		});
	}
	printLog(`搜索页解析完成\n${JSON.stringify(baseObject.search[0])}\n`);
}

// 详情页
var isChapterHtml = '';
function detail(url) {
	printLog(`开始获取详情页 ${url}`);
	let response = fetch(url, { charset: 'gbk' });
	let html = response.text();
	document = new Document(html);
	printLog('成功获取结果');

	baseObject.detail = {
		title: document.queryAttr('[property="og:novel:book_name"]', 'content'),
		author: document.queryAttr('[property="og:novel:author"]', 'content'),
		intro: document.queryText('#intro>p'),
		tag: document.queryAttr('[property="og:novel:category"]', 'content'),
		count: '',
		img: document.queryAttr('[property="og:image"]', 'content'),
		date: document.queryAttr('[property="og:novel:update_time"]', 'content'),
		last: document.queryAttr('[property="og:novel:latest_chapter_name"]', 'content'),
		url: url
	};

	isChapterHtml = html; // 目录页与详情页是同一页
	printLog(`详情页解析完成\n${JSON.stringify(baseObject.detail)}\n`);
}

// 目录页
function chapter(url) {
	let html = isChapterHtml;
	if (!html) {
		printLog(`开始获取目录页 ${url}`);
		let response = fetch(url, { charset: 'gbk' });
		html = response.text();
		printLog('成功获取目录页');
	} else {
		printLog('成功获取目录页(与详情页相同)');
	}

	let reg = 'dd><a href="([^"]+)[^>]+>([^<]+)';
	baseObject.chapter = html.match(new RegExp(reg, 'g')).map((item) => {
		let ret = item.match(reg);
		return { title: ret[2], time: '', url: ret[1] };
	});
	printLog(`目录页解析完成,共 ${baseObject.chapter.length} 章\n第一章: ${JSON.stringify(baseObject.chapter[0])}\n`);
}

// 正文页
function context(url) {
	printLog(`开始获取正文页 ${url}`);
	let response = fetch(url, { charset: 'gbk' });
	let html = response.text();
	let document = new Document(html);
	baseObject.context = document
		.querySelector('#content')
		.textNodes()
		.filter((w) => w)
		.join(`\n　　`);
	printLog(`正文解析完成\n${baseObject.context}`);
}

// 需要交给App调用的任务链(必要)
step = [(sKey) => search(sKey), () => detail(baseObject.search[0].url), () => chapter(baseObject.detail.url), () => context(baseObject.info.origin + baseObject.chapter[0].url)];
