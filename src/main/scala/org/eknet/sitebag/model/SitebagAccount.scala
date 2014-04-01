package org.eknet.sitebag.model

import spray.http.Uri
import porter.model.Ident

case class SitebagAccount(name: Ident, feeds: List[Uri], tags: List[Tag], token: Option[Token])
