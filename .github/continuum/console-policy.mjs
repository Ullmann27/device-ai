// Only the GitHub account owning this repository can issue state-changing
// console instructions. Web pages, issues and third-party comments have no authority.
export function isOwnerComment(repository, login) {
  if (typeof repository !== "string" || typeof login !== "string") return false;
  const parts=repository.split("/");
  if (parts.length!==2 || !parts[0] || !parts[1]) return false;
  const name=login.trim();
  return name.length>0 && name.toLowerCase()===parts[0].toLowerCase();
}
